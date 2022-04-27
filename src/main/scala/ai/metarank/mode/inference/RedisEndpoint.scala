package ai.metarank.mode.inference

import ai.metarank.config.Config.StateStoreConfig
import ai.metarank.config.MPath
import ai.metarank.mode.upload.Upload
import cats.effect.IO
import cats.effect.kernel.Resource
import com.github.microwww.redis.RedisServer
import io.findify.featury.values.StoreCodec

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

  case class EmbeddedRedis(host: String, port: Int, format: StoreCodec, service: RedisServer, dir: MPath)
      extends RedisEndpoint {
    override def upload: IO[Unit] = Upload.upload(dir, host, port, format).use(_ => IO.unit)

    override def close: IO[Unit] = IO { service.close() }
  }

  object EmbeddedRedis {
    def createUnsafe(port: Int) = {
      val service = new RedisServer()
      service.listener("localhost", port)
      service
    }
  }

  def create(store: StateStoreConfig, workdir: MPath) = store match {
    case StateStoreConfig.RedisConfig(host, port, format) =>
      Resource.make(IO.pure(RemoteRedis(host)))(_ => IO.unit)
    case StateStoreConfig.MemConfig(format, port) =>
      Resource.make(IO {
        EmbeddedRedis("localhost", port, format, EmbeddedRedis.createUnsafe(port), workdir / "features")
      })(_.close)
  }
}
