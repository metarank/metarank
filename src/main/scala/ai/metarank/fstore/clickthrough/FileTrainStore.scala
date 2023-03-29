package ai.metarank.fstore.clickthrough

import ai.metarank.flow.PrintProgress
import ai.metarank.fstore.TrainStore
import ai.metarank.fstore.clickthrough.FileTrainStore.FILE_BUFFER_SIZE
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.model.TrainValues
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import fs2.Stream

import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutput, DataOutputStream, File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

case class FileTrainStore(file: File, output: DataOutput, stream: OutputStream, fmt: StoreFormat, dir: File)
    extends TrainStore
    with Logging {

  override def put(cts: List[TrainValues]): IO[Unit] = IO {
    cts.foreach(fmt.ctv.encodeDelimited(_, output))
  }

  override def flush(): IO[Unit] = IO(stream.flush())

  override def getall(): fs2.Stream[IO, TrainValues] = {
    Stream(dir.listFiles().toList.sortBy(_.getName): _*)
      .flatMap(f =>
        Stream
          .bracket[IO, InputStream](IO {
            logger.info(s"reading file $f")
            new BufferedInputStream(new FileInputStream(f), FILE_BUFFER_SIZE)
          })(f => IO(f.close()))
          .flatMap(stream => {
            val input = new DataInputStream(stream)
            Stream
              .fromBlockingIterator[IO](
                iterator = Iterator.continually(fmt.ctv.decodeDelimited(input)),
                chunkSize = 32
              )
              .evalMap(x => IO.fromEither(x))
              .takeWhile(_.isDefined)
              .flatMap(x => Stream.fromOption(x))
          })
      )
      .through(PrintProgress.tap(None, "click-throughs"))
  }
}

object FileTrainStore extends Logging {
  val FILE_BUFFER_SIZE = 128 * 1024

  val format     = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss-SSS")
  def fileName() = format.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString + ".bin"

  def create(path: String, fmt: StoreFormat): Resource[IO, FileTrainStore] =
    for {
      _   <- Resource.liftK(info(s"using dir $path to store click-through events"))
      dir <- Resource.liftK(IO { new File(path) })
      _ <- Resource.liftK(
        IO.whenA(dir.exists() && !dir.isDirectory)(
          IO.raiseError(new Exception(s"a click-through persistence path $path is a file (and should be a directory)"))
        )
      )
      _ <- Resource.liftK(IO {
        if (!dir.exists()) {
          logger.info(s"path $path does not exist, creating.")
          dir.mkdirs()
        }
      })
      file <- Resource.liftK(IO(new File(List(path, File.separator, fileName()).mkString(""))))
      _    <- Resource.liftK(info(s"created file ${file.toString} for current batch of events"))
      stream <- Resource.make(IO { new BufferedOutputStream(new FileOutputStream(file), FILE_BUFFER_SIZE) })(stream =>
        IO(stream.close())
      )
    } yield {
      FileTrainStore(file, new DataOutputStream(stream), stream, fmt, dir)
    }
}
