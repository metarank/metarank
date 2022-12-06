package ai.metarank.fstore.clickthrough

import ai.metarank.config.TrainConfig.S3TrainConfig
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.S3ClickthroughStore.Upload
import ai.metarank.fstore.codec.impl.ClickthroughValuesCodec
import ai.metarank.fstore.codec.values.BinaryVCodec
import ai.metarank.model.ClickthroughValues
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  AwsCredentials,
  AwsCredentialsProvider,
  DefaultCredentialsProvider,
  StaticCredentialsProvider
}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.endpoints.Endpoint
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{
  CompleteMultipartUploadRequest,
  CompletedMultipartUpload,
  CompletedPart,
  CreateMultipartUploadRequest,
  GetObjectRequest,
  ListObjectsRequest,
  UploadPartRequest
}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.endpoints.{S3EndpointParams, S3EndpointProvider}
import software.amazon.awssdk.services.s3.endpoints.internal.DefaultS3EndpointProvider

import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream, FileInputStream, InputStream}
import java.net.URI
import java.nio.file.Files
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

case class S3ClickthroughStore(
    conf: S3TrainConfig,
    client: S3AsyncClient,
    bufferRef: Ref[IO, List[ClickthroughValues]],
    etagsRef: Ref[IO, List[CompletedPart]],
    uploadRef: Ref[IO, Upload],
    tickCancel: IO[Unit]
) extends ClickthroughStore
    with Logging {
  val codec = BinaryVCodec(false, ClickthroughValuesCodec)

  override def put(cts: List[ClickthroughValues]): IO[Unit] = for {
    _ <- bufferRef.update(last => last ++ cts)
    _ <- maybeFlush()
    _ <- maybeRollNext()
  } yield {}

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    fs2.Stream.evalSeq(listKeys()).flatMap(key => getPart(key))

  def getPart(key: String): fs2.Stream[IO, ClickthroughValues] = {
    fs2.Stream
      .eval(for {
        file     <- IO(Files.createTempFile("chunk_", ".bin"))
        request  <- IO(GetObjectRequest.builder().bucket(conf.bucket).key(key).build())
        response <- IO.fromCompletableFuture(IO(client.getObject(request, file)))
        _        <- info(s"read part $key")
      } yield {
        file
      })
      .flatMap(path =>
        fs2.Stream.bracket(IO(new FileInputStream(path.toFile)))(s => IO(s.close())).flatMap(s => read(s))
      )
  }

  def listKeys(): IO[List[String]] = for {
    request  <- IO(ListObjectsRequest.builder().bucket(conf.bucket).prefix(conf.prefix).build())
    response <- IO.fromCompletableFuture(IO(client.listObjects(request)))
    files    <- IO(response.contents().asScala.map(_.key()).toList.sorted)
    _        <- info(s"S3 list objects: $files")
  } yield {
    files
  }

  def tick(): IO[Unit] = for {
    _ <- IO.sleep(1.second)
    _ <- info("tick!")
    _ <- maybeRollNext()
    _ <- tick()
  } yield {}

  def close(): IO[Unit] = info("close()") *> tickCancel *> flushPart() *> finishMultipart()

  def maybeRollNext(): IO[Unit] = for {
    upload <- uploadRef.get
    now    <- IO(System.currentTimeMillis())
    _ <- IO.whenA(now - upload.start > conf.rollInterval.toMillis)(for {
      _ <- flushPart()
      _ <- rollNext()
    } yield {})
  } yield {}

  def maybeFlush(): IO[Unit] = for {
    buffer <- bufferRef.get
    _      <- IO.whenA(buffer.size > conf.batchSize)(flushPart())
  } yield {}

  def flushPart(): IO[Unit] = for {
    upload <- uploadRef.get
    request <- IO(
      UploadPartRequest
        .builder()
        .bucket(conf.bucket)
        .uploadId(upload.id)
        .key(upload.key)
        .partNumber(upload.num)
        .build()
    )
    buffer   <- bufferRef.getAndSet(List.empty)
    body     <- IO(AsyncRequestBody.fromBytes(write(buffer)))
    response <- IO.fromCompletableFuture(IO(client.uploadPart(request, body)))
    _ <- etagsRef.update(etags => etags :+ CompletedPart.builder().eTag(response.eTag()).partNumber(upload.num).build())
    _ <- uploadRef.set(upload.copy(num = upload.num + 1))
    _ <- info(s"part uploaded, key=${upload.key} num=${upload.num}")
  } yield {}

  def finishMultipart(): IO[Unit] = for {
    upload <- uploadRef.get
    etags  <- etagsRef.getAndSet(Nil)
    request <- IO(
      CompleteMultipartUploadRequest
        .builder()
        .bucket(conf.bucket)
        .key(upload.key)
        .uploadId(upload.id)
        .multipartUpload(CompletedMultipartUpload.builder().parts(etags: _*).build())
        .build()
    )
    response <- IO.fromCompletableFuture(IO(client.completeMultipartUpload(request)))
    _        <- info(s"Multipart upload finished, key=${upload.key} num=${upload.num}")
  } yield {}

  def rollNext(): IO[Unit] = for {
    _          <- finishMultipart()
    nextUpload <- Upload.create(conf, client)
    _          <- uploadRef.set(nextUpload)
    _          <- info(s"Part key=${nextUpload.key} num=${nextUpload.num} rolled")
  } yield {}

  def write(buf: List[ClickthroughValues]): Array[Byte] = {
    val out    = new ByteArrayOutputStream()
    val stream = new DataOutputStream(out)
    buf.foreach(c => codec.encodeDelimited(c, stream))
    stream.close()
    out.toByteArray
  }

  def read(stream: InputStream): fs2.Stream[IO, ClickthroughValues] = {
    val in = new DataInputStream(stream)
    fs2.Stream.fromBlockingIterator[IO](
      Iterator
        .continually(codec.decodeDelimited(in))
        .takeWhile {
          case Right(Some(_)) => true
          case _              => false
        }
        .collect { case Right(Some(value)) =>
          value
        },
      chunkSize = 128 * 1024
    )
  }

}

object S3ClickthroughStore extends Logging {
  case class Upload(id: String, start: Long, num: Int, key: String)

  object Upload {
    def create(conf: S3TrainConfig, client: S3AsyncClient): IO[Upload] = for {
      now <- IO(System.currentTimeMillis())
      key     = conf.prefix + "/" + makeFileName(now)
      request = CreateMultipartUploadRequest.builder().bucket(conf.bucket).key(key).build()
      uploadResponse <- IO.fromCompletableFuture(IO(client.createMultipartUpload(request)))
    } yield {
      Upload(uploadResponse.uploadId(), now, 0, key)
    }
  }

  def create(conf: S3TrainConfig): Resource[IO, S3ClickthroughStore] = {
    Resource.make(for {
      creds    <- makeCredentials(conf)
      endpoint <- makeEndpoint(conf)
      client <- IO(
        S3AsyncClient
          .builder()
          .region(Region.of(conf.region))
          .credentialsProvider(creds)
          .endpointProvider(endpoint)
          .build()
      )
      buffer <- Ref.of[IO, List[ClickthroughValues]](Nil)
      etags  <- Ref.of[IO, List[CompletedPart]](Nil)
      upload <- Ref.ofEffect(Upload.create(conf, client))
      store = S3ClickthroughStore(
        conf = conf,
        client = client,
        bufferRef = buffer,
        etagsRef = etags,
        uploadRef = upload,
        tickCancel = IO.unit
      )
      ticker <- store.tick().background.allocated
    } yield {
      store.copy(tickCancel = ticker._2)
    })(_.close())
  }

  def makeCredentials(conf: S3TrainConfig): IO[AwsCredentialsProvider] = {
    (conf.key, conf.secret) match {
      case (Some(key), Some(secret)) =>
        info("Using custom AWS credentials from config") *> IO.pure(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret))
        )
      case _ =>
        info("Using default AWS credentials chain") *> IO.pure(DefaultCredentialsProvider.create())
    }
  }

  def makeEndpoint(conf: S3TrainConfig): IO[S3EndpointProvider] = {
    conf.endpoint match {
      case None => IO.pure(new DefaultS3EndpointProvider())
      case Some(uri) =>
        info(s"Using custom S3 endpoint: $uri") *> IO(StaticEndpointProvider(uri))
    }

  }
  val format = DateTimeFormatter.ofPattern("yyyyMMdd_hhmmss")

  def makeFileName(now: Long): String = format.format(Instant.ofEpochMilli(now)) + ".bin"

  case class StaticEndpointProvider(uri: String) extends S3EndpointProvider {
    override def resolveEndpoint(endpointParams: S3EndpointParams): CompletableFuture[Endpoint] =
      CompletableFuture.completedFuture(Endpoint.builder().url(URI.create(uri)).build())
  }
}
