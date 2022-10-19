package ai.metarank.fstore.redis.client

import ai.metarank.config.StateStoreConfig.RedisCredentials
import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, PipelineConfig}
import ai.metarank.fstore.redis.client.RedisClient.ScanCursor
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.{ScanArgs, RedisClient => LettuceClient, ScanCursor => LettuceCursor}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{ByteArrayCodec, RedisCodec, StringCodec}

import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.concurrent.duration._

case class RedisClient(
    lettuce: LettuceClient,
    reader: RedisAsyncCommands[String, Array[Byte]],
    readerConn: StatefulRedisConnection[String, Array[Byte]],
    writer: RedisAsyncCommands[String, Array[Byte]],
    writerConn: StatefulRedisConnection[String, Array[Byte]],
    bufferSize: Ref[IO, Int],
    conf: PipelineConfig
) extends Logging {
  // reads
  def lrange(key: String, start: Int, end: Int): IO[List[Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.lrange(key, start, end).toCompletableFuture))
      .map(_.asScala.toList)

  def get(key: String): IO[Option[Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.get(key).toCompletableFuture)).map(Option.apply)

  def mget(keys: List[String]): IO[Map[String, Array[Byte]]] = keys match {
    case Nil => IO.pure(Map.empty)
    case _ =>
      IO.fromCompletableFuture(IO(reader.mget(keys: _*).toCompletableFuture))
        .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)
  }

  def hgetAll(key: String): IO[Map[String, Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.hgetall(key).toCompletableFuture)).map(_.asScala.toMap)

  def ping(): IO[String] =
    IO.fromCompletableFuture(IO(reader.ping().toCompletableFuture))

  def scan(cursor: String, count: Int, pattern: String): IO[ScanCursor] =
    IO.fromCompletableFuture(
      IO(reader.scan(LettuceCursor.of(cursor), ScanArgs.Builder.limit(count).`match`(pattern)).toCompletableFuture)
    ).map(sc => ScanCursor(sc.getKeys.asScala.toList, sc.getCursor))

  // writes

  def mset(values: Map[String, Array[Byte]]): IO[Unit] = {
    if (values.nonEmpty) {
      IO(writer.mset(values.asJava).toCompletableFuture).flatMap(maybeFlush)
    } else {
      IO.unit
    }
  }

  def set(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.set(key, value).toCompletableFuture).flatMap(maybeFlush)

  def hset(key: String, values: Map[String, Array[Byte]]): IO[Unit] =
    IO(writer.hset(key, values.asJava).toCompletableFuture).flatMap(maybeFlush)

  def hdel(key: String, keys: List[String]): IO[Unit] =
    IO(writer.hdel(key, keys: _*).toCompletableFuture).flatMap(maybeFlush)

  def hincrby(key: String, subkey: String, by: Int): IO[Unit] =
    IO(writer.hincrby(key, subkey, by).toCompletableFuture).flatMap(maybeFlush)

  def incrBy(key: String, by: Int): IO[Unit] =
    IO(writer.incrby(key, by).toCompletableFuture).flatMap(maybeFlush)

  def lpush(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.lpush(key, value).toCompletableFuture).flatMap(maybeFlush)

  def lpush(key: String, values: List[Array[Byte]]): IO[Unit] =
    IO(writer.lpush(key, values: _*).toCompletableFuture).flatMap(maybeFlush)

  def ltrim(key: String, start: Int, end: Int): IO[Unit] =
    IO(writer.ltrim(key, start, end).toCompletableFuture).flatMap(maybeFlush)

  def append(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.append(key, value).toCompletableFuture).flatMap(maybeFlush)

  def maybeFlush[T](lastWrite: CompletableFuture[T]): IO[Unit] = bufferSize.updateAndGet(_ + 1).flatMap {
    case cnt if cnt >= conf.maxSize =>
      debug(s"overflow pipeline flush of $cnt writes") *> doFlush(lastWrite)
    case _ => IO.unit
  }

  def doFlush[T](last: CompletableFuture[T]): IO[Unit] = for {
    _ <- bufferSize.set(0)
    _ <- IO.fromCompletableFuture(IO {
      writerConn.flushCommands()
      last
    })
  } yield {}

  private def tick(): IO[Unit] = for {
    _ <- IO.sleep(conf.flushPeriod)
    _ <- bufferSize.getAndSet(0).flatMap {
      case 0 => IO.unit
      case cnt =>
        for {
          _ <- debug(s"scheduled pipeline flush of $cnt writes")
          _ <- IO { writerConn.flushCommands() }
        } yield {}
    }
    _ <- tick()
  } yield {}

}

object RedisClient extends Logging {
  case class ScanCursor(keys: List[String], cursor: String)
  def create(
      host: String,
      port: Int,
      db: Int,
      cache: PipelineConfig,
      auth: Option[RedisCredentials]
  ): Resource[IO, RedisClient] = {
    Resource
      .make(for {
        buffer <- Ref.of[IO, Int](0)
        client <- IO { createUnsafe(host, port, db, cache, buffer, auth) }
        tickCancel <-
          if (cache.flushPeriod != 0.millis) {
            info(s"started flush timer every ${cache.flushPeriod}") *> client.tick().background.allocated.map(_._2)
          } else {
            info("periodic flushing disabled") *> IO.pure(IO.unit)
          }
      } yield {
        client -> tickCancel
      })(client => info("closing redis connection") *> IO(client._1.lettuce.close()) *> client._2)
      .map(_._1)
  }

  def createUnsafe(
      host: String,
      port: Int,
      db: Int,
      conf: PipelineConfig,
      buffer: Ref[IO, Int],
      auth: Option[RedisCredentials]
  ) = {
    val client = io.lettuce.core.RedisClient.create(s"redis://$host:$port")
    val readConnection =
      client.connect[String, Array[Byte]](RedisCodec.of(new StringCodec(), new ByteArrayCodec()))
    authenticateUnsafe(readConnection, auth)
    readConnection.sync().select(db)
    val writeConnection =
      client.connect[String, Array[Byte]](RedisCodec.of(new StringCodec(), new ByteArrayCodec()))
    authenticateUnsafe(writeConnection, auth)
    writeConnection.sync().select(db)
    writeConnection.setAutoFlushCommands(false)
    logger.info(
      s"opened read+write connection redis://$host:$port, db=$db (pipeline size: ${conf.maxSize} period: ${conf.flushPeriod})"
    )
    new RedisClient(
      client,
      readConnection.async(),
      readConnection,
      writeConnection.async(),
      writeConnection,
      buffer,
      conf
    )
  }

  def authenticateUnsafe(conn: StatefulRedisConnection[String, Array[Byte]], auth: Option[RedisCredentials]) = {
    auth match {
      case None                                     => //
      case Some(RedisCredentials(None, pass))       => conn.sync().auth(pass)
      case Some(RedisCredentials(Some(user), pass)) => conn.sync().auth(user, pass)
    }
  }

}
