package me.dfdx.metarank.store
import java.io.{ByteArrayInputStream, DataInputStream}
import java.nio.charset.StandardCharsets

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.aggregation.state.State
import redis.clients.jedis.{JedisPool, JedisPoolConfig}

import scala.concurrent.ExecutionContext

class RedisStore(endpoint: String, port: Int)(implicit ec: ExecutionContext) extends Store {
  lazy val pool = new JedisPool(endpoint, port)

  override def load[T <: State](tracker: Aggregation, scope: Aggregation.Scope)(implicit
      reader: State.Reader[T]
  ): IO[Option[T]] = for {
    client      <- IO.delay(pool.getResource)
    bytesOption <- IO.delay(Option(client.get(key(tracker, scope).getBytes(StandardCharsets.UTF_8))))
    _           <- IO.delay(client.close())
  } yield {
    bytesOption.map(bytes => reader.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def save[T <: State](tracker: Aggregation, scope: Aggregation.Scope, value: T)(implicit
      writer: State.Writer[T]
  ): IO[Unit] = for {
    client <- IO.delay(pool.getResource)
    data   <- IO.delay(writer.write(value))
    _      <- IO.delay(client.set(key(tracker, scope).getBytes(StandardCharsets.UTF_8), data))
    _      <- IO.delay(client.close())
  } yield {}
}
