package ai.metarank.fstore.clickthrough

import ai.metarank.config.TrainConfig.{CompressionType, S3TrainConfig}
import ai.metarank.fstore.TrainStore
import ai.metarank.fstore.clickthrough.S3TrainStore.{Buffer, format}
import ai.metarank.fstore.codec.VCodec
import ai.metarank.model.TrainValues
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

case class S3TrainStore(
    conf: S3TrainConfig,
    client: S3AsyncClient,
    bufferRef: Ref[IO, Buffer],
    tickCancel: IO[Unit]
) extends TrainStore
    with Logging {
  val tmpdir = System.getProperty("java.io.tmpdir")

  override def put(cts: List[TrainValues]): IO[Unit] = for {
    _ <- bufferRef.update(_.put(cts))
    _ <- maybeFlush()
  } yield {}

  override def getall(): fs2.Stream[IO, TrainValues] =
    fs2.Stream.evalSeq(listKeys()).flatMap(key => getPart(key))

  def getPart(key: String): fs2.Stream[IO, TrainValues] = {
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
    _ <- IO.sleep(conf.partInterval)
    _ <- maybeFlush()
    _ <- tick()
  } yield {}

  def close(): IO[Unit] = info("close()") *> tickCancel *> flushPart()

  override def flush(): IO[Unit] = info("forced flush") *> flushPart()

  def maybeFlush(): IO[Unit] = for {
    buffer <- bufferRef.get
    isEventOverflow = buffer.eventCount > conf.partSizeEvents
    isBytesOverflow = buffer.byteSize > conf.partSizeBytes
    isTimeUp <- IO(System.currentTimeMillis() - buffer.start > conf.partInterval.toMillis)
    _        <- IO.whenA(isEventOverflow || isBytesOverflow || isTimeUp)(flushPart())
  } yield {}

  def makeFileName(now: Long): String = format.format(Instant.ofEpochMilli(now)) + conf.compress.ext

  def flushPart(): IO[Unit] = for {
    buffer <- bufferRef.get
    _      <- bufferRef.set(Buffer(conf.compress, conf.format.ctv))
    _ <- IO.whenA(buffer.nonEmpty)(for {
      key <- IO(conf.prefix + "/" + makeFileName(System.currentTimeMillis()))
      _ <- info(
        s"flushing part key=$key size=(${FileUtils.byteCountToDisplaySize(buffer.byteSize)}, ${buffer.eventCount} events)"
      )
      request  <- IO(PutObjectRequest.builder().bucket(conf.bucket).key(key).build())
      body     <- IO(AsyncRequestBody.fromBytes(buffer.toByteArray()))
      response <- IO.fromCompletableFuture(IO(client.putObject(request, body)))
    } yield {})
  } yield {}

  def read(stream: InputStream): fs2.Stream[IO, TrainValues] = {
    val raw = conf.compress match {
      case CompressionType.GzipCompressionType => new GZIPInputStream(stream)
      case CompressionType.ZstdCompressionType => new ZstdInputStream(stream)
      case CompressionType.NoCompressionType   => stream
    }
    val in = new DataInputStream(raw)

    fs2.Stream.fromBlockingIterator[IO](
      Iterator
        .continually(conf.format.ctv.decodeDelimited(in))
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

object S3TrainStore extends Logging {

  val format = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault())

  case class Buffer(
      stream: ByteArrayOutputStream,
      wrap: OutputStream,
      out: DataOutputStream,
      eventCount: Int,
      byteSize: Int,
      codec: VCodec[TrainValues],
      start: Long
  ) {
    def isEmpty  = eventCount == 0
    def nonEmpty = !isEmpty

    def put(event: TrainValues): Buffer = {
      val extraBytes = codec.encodeDelimited(event, out)
      copy(eventCount = eventCount + 1, byteSize = byteSize + extraBytes)
    }

    def put(events: List[TrainValues]): Buffer = {
      val extraBytes = events.foldLeft(0)((size, next) => size + codec.encodeDelimited(next, out))
      copy(eventCount = eventCount + events.size, byteSize = byteSize + extraBytes)
    }

    def toByteArray() = {
      out.close()
      stream.toByteArray
    }
    def close() = wrap.close()
  }

  object Buffer {
    def apply(compress: CompressionType, codec: VCodec[TrainValues]): Buffer = {
      val stream = new ByteArrayOutputStream()
      val wrap = compress match {
        case CompressionType.GzipCompressionType => new GZIPOutputStream(stream)
        case CompressionType.ZstdCompressionType => new ZstdOutputStream(stream)
        case CompressionType.NoCompressionType   => stream
      }
      new Buffer(stream, wrap, new DataOutputStream(wrap), 0, 0, codec, System.currentTimeMillis())
    }
  }

  def create(conf: S3TrainConfig): Resource[IO, S3TrainStore] = {
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
      buffer <- Ref.of[IO, Buffer](Buffer(conf.compress, conf.format.ctv))
      store = S3TrainStore(
        conf = conf,
        client = client,
        bufferRef = buffer,
        tickCancel = IO.unit
      )
      ticker <- store.tick().background.allocated
      (_, tickerCancel) = ticker
    } yield {
      store.copy(tickCancel = tickerCancel)
    })(_.close())
  }

  def makeCredentials(conf: S3TrainConfig): IO[AwsCredentialsProvider] = {
    (conf.awsKey, conf.awsKeySecret) match {
      case (Some(key), Some(secret)) =>
        info("Using custom AWS credentials from config") *> IO.pure(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret))
        )
      case _ =>
        info("Using default AWS credentials chain") *> IO.pure(DefaultCredentialsProvider.builder().build())
    }
  }

}
