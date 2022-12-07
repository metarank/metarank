package ai.metarank.fstore.clickthrough

import ai.metarank.config.TrainConfig.{CompressionType, S3TrainConfig}
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.S3ClickthroughStore.{Buffer, format}
import ai.metarank.fstore.codec.impl.ClickthroughValuesCodec
import ai.metarank.fstore.codec.values.BinaryVCodec
import ai.metarank.model.ClickthroughValues
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import org.apache.commons.io.FileUtils
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  AwsCredentialsProvider,
  DefaultCredentialsProvider,
  StaticCredentialsProvider
}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, ListObjectsRequest, PutObjectRequest}
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream, FileInputStream, InputStream, OutputStream}
import java.net.URI
import java.nio.file.{Files, Path}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

case class S3ClickthroughStore(
    conf: S3TrainConfig,
    client: S3AsyncClient,
    bufferRef: Ref[IO, Buffer],
    tickCancel: IO[Unit]
) extends ClickthroughStore
    with Logging {
  val tmpdir = System.getProperty("java.io.tmpdir")

  override def put(cts: List[ClickthroughValues]): IO[Unit] = for {
    _ <- bufferRef.update(_.put(cts))
    _ <- maybeFlush()
  } yield {}

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    fs2.Stream.evalSeq(listKeys()).flatMap(key => getPart(key))

  def getPart(key: String): fs2.Stream[IO, ClickthroughValues] = {
    fs2.Stream
      .eval(for {
        file     <- IO(Path.of(tmpdir, key))
        _        <- IO(Files.createDirectories(file.getParent))
        request  <- IO(GetObjectRequest.builder().bucket(conf.bucket).key(key).build())
        response <- IO.fromCompletableFuture(IO(client.getObject(request, file)))
        _        <- info(s"read part $key size=${FileUtils.byteCountToDisplaySize(response.contentLength())}")
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
    _        <- info(s"S3 list objects: count=${files.size} values=$files")
  } yield {
    files
  }

  def tick(): IO[Unit] = for {
    _ <- IO.sleep(1.second)
    _ <- maybeFlush()
    _ <- tick()
  } yield {}

  def close(): IO[Unit] = info("close()") *> tickCancel *> flushPart()

  override def flush(): IO[Unit] = info("forced flush") *> flushPart()

  def maybeFlush(): IO[Unit] = for {
    buffer <- bufferRef.get
    _      <- IO.whenA((buffer.eventCount > conf.partSizeEvents) || (buffer.byteSize > conf.partSizeBytes))(flushPart())
  } yield {}

  def makeFileName(now: Long): String = format.format(Instant.ofEpochMilli(now)) + conf.compress.ext

  def flushPart(): IO[Unit] = for {
    buffer <- bufferRef.get
    _      <- bufferRef.set(Buffer(conf.compress))
    _ <- IO.whenA(buffer.nonEmpty)(for {
      key     <- IO(conf.prefix + "/" + makeFileName(System.currentTimeMillis()))
      request <- IO(PutObjectRequest.builder().bucket(conf.bucket).key(key).build())
      _ <- info(
        s"flushing part key=$key size=(${FileUtils.byteCountToDisplaySize(buffer.byteSize)}, ${buffer.eventCount} events)"
      )
      body     <- IO(AsyncRequestBody.fromBytes(buffer.toByteArray()))
      response <- IO.fromCompletableFuture(IO(client.putObject(request, body)))
    } yield {})
  } yield {}

  def read(stream: InputStream): fs2.Stream[IO, ClickthroughValues] = {
    val raw = conf.compress match {
      case CompressionType.GzipCompressionType => new GZIPInputStream(stream)
      case CompressionType.ZstdCompressionType => new ZstdInputStream(stream)
      case CompressionType.NoCompressionType   => stream
    }
    val in = new DataInputStream(raw)

    fs2.Stream.fromBlockingIterator[IO](
      Iterator
        .continually(S3ClickthroughStore.codec.decodeDelimited(in))
        .takeWhile {
          case Right(Some(_)) => true
          case _              => false
        }
        .collect { case Right(Some(value)) =>
          value
        },
      chunkSize = 1024
    )
  }

}

object S3ClickthroughStore extends Logging {

  val codec  = BinaryVCodec(false, ClickthroughValuesCodec)
  val format = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault())

  case class Buffer(
      stream: ByteArrayOutputStream,
      wrap: OutputStream,
      out: DataOutputStream,
      eventCount: Int,
      byteSize: Int
  ) {
    def isEmpty  = eventCount == 0
    def nonEmpty = !isEmpty

    def put(event: ClickthroughValues): Buffer = {
      codec.encodeDelimited(event, out)
      copy(eventCount = eventCount + 1, byteSize = stream.size())
    }

    def put(events: List[ClickthroughValues]): Buffer = {
      events.foreach(codec.encodeDelimited(_, out))
      copy(eventCount = eventCount + events.size, byteSize = stream.size())
    }

    def toByteArray() = stream.toByteArray
    def close()       = wrap.close()
  }

  object Buffer {
    def apply(compress: CompressionType): Buffer = {
      val stream = new ByteArrayOutputStream()
      val wrap = compress match {
        case CompressionType.GzipCompressionType => new GZIPOutputStream(stream)
        case CompressionType.ZstdCompressionType => new ZstdOutputStream(stream)
        case CompressionType.NoCompressionType   => stream
      }
      new Buffer(stream, wrap, new DataOutputStream(wrap), 0, 0)
    }
  }

  def create(conf: S3TrainConfig): Resource[IO, S3ClickthroughStore] = {
    Resource.make(for {
      creds <- makeCredentials(conf)
      clientBuilder <- IO(
        S3AsyncClient
          .builder()
          .region(Region.of(conf.region))
          .credentialsProvider(creds)
          .forcePathStyle(conf.endpoint.isDefined)
      )
      client = conf.endpoint match {
        case Some(endpoint) => clientBuilder.endpointOverride(URI.create(endpoint)).build()
        case None           => clientBuilder.build()
      }
      buffer <- Ref.of[IO, Buffer](Buffer(conf.compress))
      store = S3ClickthroughStore(
        conf = conf,
        client = client,
        bufferRef = buffer,
        tickCancel = IO.unit
      )
      ticker <- store.tick().background.allocated
    } yield {
      store.copy(tickCancel = ticker._2)
    })(_.close())
  }

  def makeCredentials(conf: S3TrainConfig): IO[AwsCredentialsProvider] = {
    (conf.awsKey, conf.awsKeySecret) match {
      case (Some(key), Some(secret)) =>
        info("Using custom AWS credentials from config") *> IO.pure(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret))
        )
      case _ =>
        info("Using default AWS credentials chain") *> IO.pure(DefaultCredentialsProvider.create())
    }
  }

}
