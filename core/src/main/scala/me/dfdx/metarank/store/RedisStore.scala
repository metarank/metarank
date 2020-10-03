package me.dfdx.metarank.store

import java.nio.charset.StandardCharsets

import scala.collection.JavaConverters._
import cats.effect.{IO, Resource}
import me.dfdx.metarank.aggregation.Scope
import me.dfdx.metarank.store.RedisStore.{RedisMapStore, RedisValueStore}
import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import scala.concurrent.ExecutionContext

class RedisStore(endpoint: String, port: Int)(implicit ec: ExecutionContext) extends Store {
  lazy val pool   = new JedisPool(endpoint, port)
  lazy val client = Resource[IO, Jedis](IO(pool.getResource).map(jedis => jedis -> IO(jedis.close())))

  override def kv[K, V](desc: StateDescriptor.MapStateDescriptor[K, V], scope: Scope): MapState[K, V] =
    new RedisMapStore(scope, client, desc.kc, desc.vc)

  override def value[T](desc: StateDescriptor.ValueStateDescriptor[T], scope: Scope): ValueState[T] =
    new RedisValueStore[T](scope, client, desc.codec)
}

object RedisStore {
  class RedisValueStore[T](scope: Scope, client: Resource[IO, Jedis], codec: Codec[T]) extends ValueState[T] {
    override def get(): IO[Option[T]] = {
      client.use(jedis => IO(Option(jedis.get(Scope.write(scope).getBytes(StandardCharsets.UTF_8))).map(codec.read)))
    }

    override def put(value: T): IO[Unit] =
      client.use(jedis => IO(jedis.set(Scope.write(scope).getBytes(StandardCharsets.UTF_8), codec.write(value))))

    override def delete(): IO[Unit] =
      client.use(jedis => IO(jedis.del(Scope.write(scope).getBytes(StandardCharsets.UTF_8))))
  }

  class RedisMapStore[K, V](scope: Scope, client: Resource[IO, Jedis], kc: KeyCodec[K], vc: Codec[V])
      extends MapState[K, V] {
    override def get(key: K): IO[Option[V]] = {
      client.use(jedis =>
        IO(
          Option(
            jedis.hget(
              Scope.write(scope).getBytes(StandardCharsets.UTF_8),
              kc.write(key).getBytes(StandardCharsets.UTF_8)
            )
          ).map(vc.read)
        )
      )
    }

    override def put(key: K, value: V): IO[Unit] =
      client.use(jedis =>
        IO(
          jedis.hset(
            Scope.write(scope).getBytes(StandardCharsets.UTF_8),
            kc.write(key).getBytes(StandardCharsets.UTF_8),
            vc.write(value)
          )
        )
      )

    override def delete(key: K): IO[Unit] =
      client.use(jedis =>
        IO(
          jedis.hdel(
            Scope.write(scope).getBytes(StandardCharsets.UTF_8),
            kc.write(key).getBytes(StandardCharsets.UTF_8)
          )
        )
      )

  }
}
