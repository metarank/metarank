package ai.metarank.config

import io.circe.Decoder
import better.files.Dsl.cwd
import better.files.File
import org.apache.flink.core.fs.Path

import scala.util.Random

sealed trait MPath {
  def path: String
  def uri: String
  def flinkPath: Path = new Path(uri)

  def /(next: String): MPath
  def child(next: String) = /(next)
}

object MPath {
  case class S3Path(bucket: String, path: String) extends MPath {
    def uri = s"s3://$bucket/$path"

    override def /(next: String): MPath = copy(path = path + "/" + next)
  }
  case class LocalPath(path: String) extends MPath {
    def file = File(path)
    def uri  = s"file://$path"

    override def /(next: String): MPath = copy(path = path + "/" + next)
  }

  val s3Pattern            = "s3://([a-z0-9\\.\\-]{3,})/([ a-zA-Z0-9\\!\\-_\\.\\*'\\(\\)]+)".r
  val localSchemePattern3  = "file:///(.+)".r
  val localSchemePattern2  = "file://(.+)".r
  val localAbsolutePattern = "/(.+)".r
  val localRelativePattern = "\\./(.+)".r

  implicit val decoder: Decoder[MPath] = Decoder.decodeString.map[MPath](string => MPath(string))

  def apply(path: String): MPath = path match {
    case s3Pattern(bucket, path)    => S3Path(bucket, path)
    case localSchemePattern3(path)  => LocalPath("/" + path)
    case localSchemePattern2(path)  => LocalPath("/" + path)
    case localAbsolutePattern(path) => LocalPath("/" + path)
    case localRelativePattern(path) => LocalPath((cwd / path).toString())
    case other                      => LocalPath((cwd / other).toString())
  }

  def apply(file: File) = LocalPath(file.toString())

}
