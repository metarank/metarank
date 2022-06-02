package ai.metarank.util.fs

import ai.metarank.config.MPath.LocalPath
import cats.effect.IO
import org.apache.commons.io.IOUtils
import scala.jdk.CollectionConverters._

import java.io.{ByteArrayInputStream, File, FileInputStream, FileOutputStream}

case class LocalFS() extends FS[LocalPath] {
  override def read(path: LocalPath): IO[Array[Byte]] = IO {
    val stream = new FileInputStream(new File(path.path))
    val bytes  = IOUtils.toByteArray(stream)
    stream.close()
    bytes
  }

  override def write(path: LocalPath, bytes: Array[Byte]): IO[Unit] = IO {
    val stream = new FileOutputStream(new File(path.path))
    IOUtils.copy(new ByteArrayInputStream(bytes), stream)
    stream.close()
  }

  override def listRecursive(path: LocalPath): IO[List[LocalPath]] = IO {
    val result = listChildren(path)
    result
  }

  private def listChildren(path: LocalPath): List[LocalPath] = {
    val file = new File(path.path)
    if (file.isDirectory) {
      file.listFiles().toList.map(f => LocalPath(f.toString)).flatMap(f => listChildren(f))
    } else {
      List(path)
    }
  }
}
