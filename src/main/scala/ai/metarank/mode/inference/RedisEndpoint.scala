package ai.metarank.mode.inference

import ai.metarank.mode.upload.{Upload, UploadCmdline}
import cats.effect.IO
import cats.effect.kernel.Resource
import com.github.microwww.redis.RedisServer
import io.findify.featury.values.StoreCodec.JsonCodec

sealed trait RedisEndpoint {
  def host: String
  def upload: IO[Unit]
  def close: IO[Unit]
}

object RedisEndpoint {
  case class RemoteRedis(host: String) extends RedisEndpoint {
    override def upload: IO[Unit] = IO.unit
    override def close: IO[Unit]  = IO.unit
  }

  case class EmbeddedRedis(host: String, service: RedisServer, dir: String) extends RedisEndpoint {
    override def upload: IO[Unit] = Upload.run(UploadCmdline(host, 6379, JsonCodec, dir, 1024)).map(_ => {})
    override def close: IO[Unit]  = IO { service.close() }
  }

  def create(dir: Option[String], host: Option[String], port: Int): Resource[IO, RedisEndpoint] = (dir, host) match {
    case (Some(dir), None) =>
      Resource.make(IO {
        val service = new RedisServer()
        service.listener("localhost", port)
        EmbeddedRedis("localhost", service, dir)
      })(_.close)
    case (None, Some(host)) => Resource.make(IO.pure(RemoteRedis(host)))(_ => IO.unit)
    case _ =>
      Resource.raiseError[IO, RedisEndpoint, Throwable](
        new IllegalArgumentException("you need to specify either data dir or remote redis host")
      )
  }
}
