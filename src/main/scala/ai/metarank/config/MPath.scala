package ai.metarank.config

import io.circe.Decoder
import better.files.Dsl.cwd

sealed trait MPath {
  def path: String
}

object MPath {
  case class S3Path(bucket: String, path: String) extends MPath
  case class LocalPath(path: String)              extends MPath

  val s3Pattern            = "s3://([a-z0-9\\.\\-]{3,})/([ a-zA-Z0-9\\!\\-_\\.\\*'\\(\\)]+)".r
  val localSchemePattern3  = "local:///(.+)".r
  val localSchemePattern2  = "local://(.+)".r
  val localAbsolutePattern = "/(.+)".r
  val localRelativePattern = "(.*)".r

  implicit val decoder: Decoder[MPath] = Decoder.decodeString.map[MPath](string => MPath(string))

  def apply(path: String): MPath = path match {
    case s3Pattern(bucket, path)    => S3Path(bucket, path)
    case localSchemePattern3(path)  => LocalPath("/" + path)
    case localSchemePattern2(path)  => LocalPath("/" + path)
    case localAbsolutePattern(path) => LocalPath("/" + path)
    case other                      => LocalPath((cwd / other).toString())
  }

}
