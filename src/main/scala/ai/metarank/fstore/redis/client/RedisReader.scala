package ai.metarank.fstore.redis.client

import cats.effect.IO
import cats.effect.kernel.Resource
import io.lettuce.core.{RedisClient, ScanArgs}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{RedisCodec, StringCodec}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case class RedisReader(
    client: RedisClient,
    commands: RedisAsyncCommands[String, String]
) {
  def lrange(key: String, start: Int, end: Int): IO[List[String]] =
    IO.fromCompletableFuture(IO(commands.lrange(key, start, end).toCompletableFuture)).map(_.asScala.toList)

  def get(key: String): IO[Option[String]] =
    IO.fromCompletableFuture(IO(commands.get(key).toCompletableFuture)).map(Option.apply)

  def mget(keys: List[String]): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.mget(keys: _*).toCompletableFuture))
      .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)

  def hget(key: String, subkeys: List[String]): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.hmget(key, subkeys: _*).toCompletableFuture))
      .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)

  def hgetAll(key: String): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.hgetall(key).toCompletableFuture)).map(_.asScala.toMap)

  def ping(): IO[String] =
    IO.fromCompletableFuture(IO(commands.ping().toCompletableFuture))
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
