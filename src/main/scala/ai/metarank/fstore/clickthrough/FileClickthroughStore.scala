package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.FileClickthroughStore.FileWriter
import ai.metarank.fstore.redis.codec.StoreFormat
import ai.metarank.model.ClickthroughValues
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import fs2.io.file.{Files, Path}

import java.io.{File, FileOutputStream, OutputStream}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

case class FileClickthroughStore(dir: String, file: Ref[IO, FileWriter], fmt: StoreFormat, max: Int = 1024)
    extends ClickthroughStore {
  override def put(cts: List[ClickthroughValues]): IO[Unit] = for {
    writer <- file.get
    _      <- IO(cts.foreach(writer.write))
    _      <- IO.whenA(writer.count > max)(IO(writer.close()) *> file.set(FileWriter(dir, fmt)))
  } yield {}

  override def getall(): fs2.Stream[IO, ClickthroughValues] = {
    ???//Files[IO].list(Path(dir)).flatMap(file => Files[IO].re)
  }
}

object FileClickthroughStore {
  val fileFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
  case class FileWriter(stream: OutputStream, fmt: StoreFormat, var count: Int = 0) {
    def write(ct: ClickthroughValues): Unit = {
      stream.write(fmt.ctv.encode(ct))
      count += 1
    }
    def close() = {
      stream.close()
    }
  }
  object FileWriter {
    def apply(dir: String, fmt: StoreFormat): FileWriter = {
      val file = new File(s"$dir/${fileFormat.format(LocalDateTime.now())}")
      FileWriter(new FileOutputStream(file), fmt)
    }
  }
  def create(dir: String, fmt: StoreFormat): Resource[IO, FileClickthroughStore] =
    Resource.make[IO, FileClickthroughStore](
      Ref.of[IO, FileWriter](FileWriter(dir, fmt)).map(w => FileClickthroughStore(dir, w, fmt))
    )(fs => fs.file.get.map(_.close()))
}
