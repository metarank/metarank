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
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  HeadObjectRequest,
  ListObjectsV2Request,
  PutObjectRequest
}
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream, FileInputStream, InputStream}
import java.net.URI
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.UUID
import scala.jdk.CollectionConverters.*
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
    fs2.Stream
      .evalSeq(listKeys())
      .evalFilter(key =>
        CompressionType.fromKey(key) match {
          case Some(_) => IO.pure(true)
          case None    => warn(s"part $key has an unsupported extension (expected .gz/.zst/.bin), skipping").as(false)
        }
      )
      .flatMap(key =>
        // A single truncated/corrupt part (e.g. left behind by an interrupted or
        // concurrent write) must not abort the whole training run: log it and skip
        // the rest of that part, keeping the records already read from it and every
        // other part.
        getPart(key).handleErrorWith(e =>
          fs2.Stream.exec(warn(s"skipping unreadable train part $key: ${e.getMessage}"))
        )
      )

  def getPart(key: String): fs2.Stream[IO, TrainValues] = {
    fs2.Stream
      .eval(for {
        file <- IO(Path.of(tmpdir, key))
        _    <- IO(Files.createDirectories(file.getParent))
        head <- IO.fromCompletableFuture(
          IO(client.headObject(HeadObjectRequest.builder().bucket(conf.bucket).key(key).build()))
        )
        remoteSize <- IO(head.contentLength().longValue())
        cached     <- IO(Files.exists(file) && Files.size(file) == remoteSize)
        _ <-
          if (cached) info(s"found part $key in local cache, size=${FileUtils.byteCountToDisplaySize(remoteSize)}")
          else downloadPart(key, file, remoteSize)
      } yield {
        file
      })
      .flatMap(path =>
        fs2.Stream.bracket(IO(new FileInputStream(path.toFile)))(s => IO(s.close())).flatMap(s => read(s, key))
      )
  }

  def downloadPart(key: String, file: Path, size: Long): IO[Unit] = for {
    tmp     <- IO(file.resolveSibling(file.getFileName.toString + ".tmp." + UUID.randomUUID().toString.take(8)))
    request <- IO(GetObjectRequest.builder().bucket(conf.bucket).key(key).build())
    _ <- IO
      .fromCompletableFuture(IO(client.getObject(request, tmp)))
      .guaranteeCase(outcome => IO.whenA(!outcome.isSuccess)(IO(Files.deleteIfExists(tmp)).void))
    _ <- IO(Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE))
    _ <- info(s"downloaded part $key size=${FileUtils.byteCountToDisplaySize(size)}")
  } yield {}

  def listKeys(): IO[List[String]] = {
    // S3 caps a list response at 1000 keys
    def loop(token: Option[String], acc: List[String]): IO[List[String]] = for {
      request <- IO {
        val builder = ListObjectsV2Request.builder().bucket(conf.bucket).prefix(conf.prefix)
        token.foreach(builder.continuationToken)
        builder.build()
      }
      response <- IO.fromCompletableFuture(IO(client.listObjectsV2(request)))
      keys = acc ++ response.contents().asScala.map(_.key()).toList
      // isTruncated and nextContinuationToken are boxed and may be absent from the response
      next = Option(response.nextContinuationToken()).filter(_ => Option(response.isTruncated).exists(_.booleanValue))
      result <- next match {
        case Some(token) => loop(Some(token), keys)
        case None        => IO.pure(keys)
      }
    } yield result

    for {
      files <- loop(None, Nil).map(_.sorted)
      _     <- info(s"S3 list objects: count=${files.size}")
    } yield files
  }

  def tick(): IO[Unit] = for {
    _ <- IO.sleep(conf.partInterval)
    _ <- maybeFlush().handleErrorWith(ex => error(s"periodic flush failed: ${ex.getMessage}", ex))
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

  def makeFileName(now: Long): String =
    format.format(Instant.ofEpochMilli(now)) + "_" + UUID.randomUUID().toString.take(8) + conf.compress.ext

  def flushPart(): IO[Unit] = for {
    // getAndSet atomically claims the current buffer and installs a fresh one, so the claimed
    // buffer is owned solely by this fiber: no concurrent put can append to it, and a second
    // flush observes the empty replacement instead of re-uploading the same part.
    buffer <- bufferRef.getAndSet(Buffer(conf.compress, conf.format.ctv))
    _ <- IO.whenA(buffer.nonEmpty)(for {
      key <- IO(conf.prefix + "/" + makeFileName(System.currentTimeMillis()))
      _ <- info(
        s"flushing part key=$key size=(${FileUtils.byteCountToDisplaySize(buffer.byteSize)}, ${buffer.eventCount} events)"
      )
      request <- IO(PutObjectRequest.builder().bucket(conf.bucket).key(key).build())
      body    <- IO(AsyncRequestBody.fromBytes(buffer.toByteArray()))
      _       <- IO.fromCompletableFuture(IO(client.putObject(request, body)))
    } yield {})
  } yield {}

  def read(stream: InputStream, key: String): fs2.Stream[IO, TrainValues] = {
    val compress = CompressionType.fromKey(key).getOrElse(conf.compress)
    val raw = compress match {
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
          case Right(None)    => false
          case Left(ex) =>
            logger.warn(s"failed to decode a record in part $key, skipping the rest of the part", ex)
            false
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

  // Immutable accumulator: holds the already-encoded, delimited bytes of each put as plain
  // (uncompressed) chunks. Keeping no live OutputStream means a Buffer can be safely shared
  // across fibers and recomputed on a Ref.update CAS retry without corrupting a deflater or
  // hitting "write beyond end of stream" when a concurrent flush replaces the buffer.
  case class Buffer(
      chunks: Vector[Array[Byte]],
      eventCount: Int,
      byteSize: Int,
      codec: VCodec[TrainValues],
      compress: CompressionType,
      start: Long
  ) {
    def isEmpty  = eventCount == 0
    def nonEmpty = !isEmpty

    def put(event: TrainValues): Buffer = put(List(event))

    def put(events: List[TrainValues]): Buffer =
      if (events.isEmpty) this
      else {
        val bytes      = new ByteArrayOutputStream()
        val out        = new DataOutputStream(bytes)
        val extraBytes = events.foldLeft(0)((size, next) => size + codec.encodeDelimited(next, out))
        out.close()
        copy(
          chunks = chunks :+ bytes.toByteArray,
          eventCount = eventCount + events.size,
          byteSize = byteSize + extraBytes
        )
      }

    def toByteArray(): Array[Byte] = {
      val stream = new ByteArrayOutputStream()
      val wrap = compress match {
        case CompressionType.GzipCompressionType => new GZIPOutputStream(stream)
        case CompressionType.ZstdCompressionType => new ZstdOutputStream(stream)
        case CompressionType.NoCompressionType   => stream
      }
      chunks.foreach(chunk => wrap.write(chunk))
      wrap.close()
      stream.toByteArray
    }
  }

  object Buffer {
    def apply(compress: CompressionType, codec: VCodec[TrainValues]): Buffer =
      new Buffer(Vector.empty, 0, 0, codec, compress, System.currentTimeMillis())
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
