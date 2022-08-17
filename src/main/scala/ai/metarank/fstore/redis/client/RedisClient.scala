package ai.metarank.fstore.redis.client

import ai.metarank.fstore.redis.client.RedisClient.ScanCursor
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import io.lettuce.core.{ScanArgs, RedisClient => LettuceClient, ScanCursor => LettuceCursor}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{RedisCodec, StringCodec}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case class RedisClient(
    lettuce: LettuceClient,
    commands: RedisAsyncCommands[String, String]
) {
  def lrange(key: String, start: Int, end: Int): IO[List[String]] =
    IO.fromCompletableFuture(IO(commands.lrange(key, start, end).toCompletableFuture)).map(_.asScala.toList)

  def get(key: String): IO[Option[String]] =
    IO.fromCompletableFuture(IO(commands.get(key).toCompletableFuture)).map(Option.apply)

  def mget(keys: List[String]): IO[Map[String, String]] = keys match {
    case Nil => IO.pure(Map.empty)
    case _ =>
      IO.fromCompletableFuture(IO(commands.mget(keys: _*).toCompletableFuture))
        .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)
  }

  def mset(values: Map[String, String]) =
    IO.fromCompletableFuture(IO(commands.mset(values.asJava).toCompletableFuture))

  def set(key: String, value: String): IO[String] =
    IO.fromCompletableFuture(IO(commands.set(key, value).toCompletableFuture))

  def hget(key: String, subkeys: List[String]): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.hmget(key, subkeys: _*).toCompletableFuture))
      .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)

  def hset(key: String, values: Map[String, String]): IO[Long] =
    IO.fromCompletableFuture(IO(commands.hset(key, values.asJava).toCompletableFuture)).map(_.longValue())

  def hdel(key: String, keys: List[String]): IO[Long] =
    IO.fromCompletableFuture(IO(commands.hdel(key, keys: _*).toCompletableFuture)).map(_.longValue())

  def hgetAll(key: String): IO[Map[String, String]] =
    IO.fromCompletableFuture(IO(commands.hgetall(key).toCompletableFuture)).map(_.asScala.toMap)

  def hincrby(key: String, subkey: String, by: Int): IO[Long] =
    IO.fromCompletableFuture(IO(commands.hincrby(key, subkey, by).toCompletableFuture)).map(_.longValue())

  def ping(): IO[String] =
    IO.fromCompletableFuture(IO(commands.ping().toCompletableFuture))

  def incrBy(key: String, by: Int): IO[Long] =
    IO.fromCompletableFuture(IO(commands.incrby(key, by).toCompletableFuture)).map(_.longValue())

  def lpush(key: String, value: String): IO[Long] =
    IO.fromCompletableFuture(IO(commands.lpush(key, value).toCompletableFuture)).map(_.longValue())

  def lpush(key: String, values: List[String]): IO[Long] =
    IO.fromCompletableFuture(IO(commands.lpush(key, values: _*).toCompletableFuture)).map(_.longValue())

  def ltrim(key: String, start: Int, end: Int): IO[String] =
    IO.fromCompletableFuture(IO(commands.ltrim(key, start, end).toCompletableFuture))

  def scan(cursor: String, count: Int): IO[ScanCursor] =
    IO.fromCompletableFuture(
      IO(commands.scan(LettuceCursor.of(cursor), ScanArgs.Builder.limit(count)).toCompletableFuture)
    ).map(sc => ScanCursor(sc.getKeys.asScala.toList, sc.getCursor))

  def append(key: String, value: String): IO[Long] =
    IO.fromCompletableFuture(IO(commands.append(key, value).toCompletableFuture)).map(_.longValue())
}

object RedisClient extends Logging {
  case class ScanCursor(keys: List[String], cursor: String)
  def create(host: String, port: Int, db: Int): Resource[IO, RedisClient] = {
    Resource.make(IO {
      val client = io.lettuce.core.RedisClient.create(s"redis://$host:$port")
      val conn   = client.connect[String, String](RedisCodec.of(new StringCodec(), new StringCodec()))
      conn.sync().select(db)
      logger.info(s"opened connection redis://$host:$port, db=$db")
      new RedisClient(client, conn.async())
    })(client => info("closing redis connection") *> IO(client.lettuce.close()))
  }

}
