package ai.metarank.fstore.redis.client

import cats.effect.IO
import cats.effect.kernel.Resource
import io.lettuce.core.RedisClient
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{RedisCodec, StringCodec}
import scala.jdk.CollectionConverters._

case class RedisReader(
    client: RedisClient,
    commands: RedisAsyncCommands[String, String]
) {
  def lrange(key: String, start: Int, end: Int): IO[List[String]] =
    IO.fromCompletableFuture(IO(commands.lrange(key, start, end).toCompletableFuture)).map(_.asScala.toList)

  def get(key: String): IO[Option[String]] =
    IO.fromCompletableFuture(IO(commands.get(key).toCompletableFuture)).map(Option.apply)

  def hgetAll(key: String): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.hgetall(key).toCompletableFuture)).map(_.asScala.toMap)
}

object RedisReader {
  def create(host: String, port: Int, db: Int): Resource[IO, RedisReader] = {
    Resource.make(IO {
      val client = io.lettuce.core.RedisClient.create(s"redis://$host:$port")
      val conn   = client.connect[String, String](RedisCodec.of(new StringCodec(), new StringCodec()))
      conn.sync().select(db)
      new RedisReader(client, conn.async())
    })(client => IO(client.client.close()))
  }

}
