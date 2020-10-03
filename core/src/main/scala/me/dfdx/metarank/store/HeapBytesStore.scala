package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import me.dfdx.metarank.aggregation.Scope
import me.dfdx.metarank.store.HeapBytesStore.{HeapMapStore, HeapValueStore}
import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}

class HeapBytesStore extends Store {
  val byteCache = Scaffeine().build[String, ByteBuffer]()

  override def kv[K, V](desc: StateDescriptor.MapStateDescriptor[K, V], scope: Scope): MapState[K, V] =
    new HeapMapStore(scope, byteCache, desc.kc, desc.vc)

  override def value[T](desc: StateDescriptor.ValueStateDescriptor[T], scope: Scope): ValueState[T] =
    new HeapValueStore[T](scope, byteCache, desc.codec)
}

object HeapBytesStore {
  def apply() = new HeapBytesStore()
  class HeapMapStore[K, V](scope: Scope, cache: Cache[String, ByteBuffer], kc: KeyCodec[K], vc: Codec[V])
      extends MapState[K, V] {
    override def get(key: K): IO[Option[V]] = IO {
      cache.getIfPresent(kc.write(scope, key)).map(bb => vc.read(bb.array()))
    }

    override def put(key: K, value: V): IO[Unit] = IO {
      cache.put(kc.write(scope, key), ByteBuffer.wrap(vc.write(value)))
    }

    override def delete(key: K): IO[Unit] = IO {
      cache.invalidate(kc.write(scope, key))
    }
  }

  class HeapValueStore[T](scope: Scope, cache: Cache[String, ByteBuffer], c: Codec[T]) extends ValueState[T] {
    override def get(): IO[Option[T]] = IO {
      cache.getIfPresent(ByteBuffer.wrap(scope.key.getBytes(StandardCharsets.UTF_8))).map(bb => c.read(bb.array()))
    }

    override def put(value: T): IO[Unit] = IO {
      cache.put(ByteBuffer.wrap(scope.key.getBytes(StandardCharsets.UTF_8)), ByteBuffer.wrap(c.write(value)))
    }

    override def delete(): IO[Unit] = IO {
      cache.invalidate(ByteBuffer.wrap(scope.key.getBytes(StandardCharsets.UTF_8)))
    }
  }
}
