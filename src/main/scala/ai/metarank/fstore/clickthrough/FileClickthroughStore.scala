package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.FileClickthroughStore.FILE_BUFFER_SIZE
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.model.ClickthroughValues
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import fs2.Stream

import java.io.{
  BufferedInputStream,
  BufferedOutputStream,
  DataInputStream,
  DataOutput,
  DataOutputStream,
  File,
  FileInputStream,
  FileOutputStream,
  InputStream,
  OutputStream
}
import java.nio.file.{Files, Paths}

case class FileClickthroughStore(file: File, output: DataOutput, stream: OutputStream, fmt: StoreFormat)
    extends ClickthroughStore {

  override def put(cts: List[ClickthroughValues]): IO[Unit] = IO {
    cts.foreach(fmt.ctv.encodeDelimited(_, output))
  }

  override def flush(): IO[Unit] = IO(stream.flush())

  override def getall(): fs2.Stream[IO, ClickthroughValues] = {
    Stream
      .bracket[IO, InputStream](IO { new BufferedInputStream(new FileInputStream(file), FILE_BUFFER_SIZE) })(f =>
        IO { f.close() }
      )
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
  }
}

object FileClickthroughStore {
  val FILE_BUFFER_SIZE = 128 * 1024

  def create(path: String, fmt: StoreFormat): Resource[IO, FileClickthroughStore] = for {
    file <- Resource.liftK(IO { new File(path) })
    stream <- Resource.make(IO { new BufferedOutputStream(new FileOutputStream(file, true), FILE_BUFFER_SIZE) })(
      stream => IO(stream.close())
    )
  } yield {
    FileClickthroughStore(file, new DataOutputStream(stream), stream, fmt)
  }
}
