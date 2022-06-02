package ai.metarank.util.fs

import java.io.{BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader}
import scala.jdk.CollectionConverters._

object LineReader {
  def lines(bytes: Array[Byte]): List[String] = {
    val stream   = new InputStreamReader(new ByteArrayInputStream(bytes))
    val buffered = new BufferedReader(stream)
    val result   = buffered.lines().iterator().asScala.toList
    buffered.close()
    result
  }
}
