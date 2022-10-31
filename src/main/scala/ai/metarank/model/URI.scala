package ai.metarank.model

import io.lettuce.core.RedisURI

import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

sealed trait URI

object URI {
  val uriFilePattern = "file://?(/.*)".r
  val absFilePattern = "(/.*)".r
  val schemaPattern  = "([a-z0-9]+)://.*".r
  case class LocalURI(path: String) extends URI
  object LocalURI {
    def cwd = Paths.get(".").toAbsolutePath.normalize().toString
  }

  case object NullURI extends URI

  val s3pattern = "s3:///?([a-z0-9\\.\\-]{3,63})/(.*)".r
  case class S3URI(bucket: String, prefix: String) extends URI

  case class RedisURI(host: String, port: Int, user: Option[String] = None, password: Option[String] = None) extends URI

  def parse(string: String): Either[Throwable, URI] = string match {
    case "null" | "/dev/null" => Right(NullURI)
    case s3pattern(bucket, prefix) =>
      Right(S3URI(bucket, prefix))
    case uriFilePattern(path) => Right(LocalURI(path))
    case absFilePattern(path) => Right(LocalURI(path))
    case schemaPattern("redis") =>
      Try(io.lettuce.core.RedisURI.create(string)) match {
        case Failure(exception) => Left(exception)
        case Success(value) =>
          val creds = value.getCredentialsProvider.resolveCredentials().block()
          Right(
            RedisURI(
              host = value.getHost,
              port = value.getPort,
              user = Option(creds.getUsername),
              password = Option(creds.getPassword).map(chars => new String(chars))
            )
          )
      }
    case schemaPattern(other) => Left(new Exception(s"schema $other is not supported"))
    case other                => Right(LocalURI(LocalURI.cwd + "/" + other))
  }
}
