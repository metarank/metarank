package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, DataInputStream}
import java.nio.charset.StandardCharsets

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.State
import me.dfdx.metarank.store.state.State.ValueState
import redis.clients.jedis.{JedisPool, JedisPoolConfig}

import scala.concurrent.ExecutionContext

class RedisStore(endpoint: String, port: Int)(implicit ec: ExecutionContext) extends Store {
  lazy val pool = new JedisPool(endpoint, port)

  override def get[T](desc: ValueState[T], scope: Aggregation.Scope): IO[Option[T]] = for {
    client      <- IO.delay(pool.getResource)
    bytesOption <- IO.delay(Option(client.get(keystr(desc, scope).getBytes(StandardCharsets.UTF_8))))
    _           <- IO.delay(client.close())
  } yield {
    bytesOption.map(bytes => desc.codec.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def put[T](desc: ValueState[T], scope: Aggregation.Scope, value: T): IO[Unit] = for {
    client <- IO.delay(pool.getResource)
    data   <- IO.delay(desc.codec.write(value))
    _      <- IO.delay(client.set(keystr(desc, scope).getBytes(StandardCharsets.UTF_8), data))
    _      <- IO.delay(client.close())
  } yield {}

  override def get[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K): IO[Option[V]] = for {
    client      <- IO.delay(pool.getResource)
    bytesOption <- IO.delay(Option(client.get(keystr(desc, scope, key)(desc.kc).getBytes(StandardCharsets.UTF_8))))
    _           <- IO.delay(client.close())
  } yield {
    bytesOption.map(bytes => desc.vc.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def put[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K, value: V): IO[Unit] = for {
    client <- IO.delay(pool.getResource)
    data   <- IO.delay(desc.vc.write(value))
    _      <- IO.delay(client.set(keystr(desc, scope, key)(desc.kc).getBytes(StandardCharsets.UTF_8), data))
    _      <- IO.delay(client.close())
  } yield {}
}
