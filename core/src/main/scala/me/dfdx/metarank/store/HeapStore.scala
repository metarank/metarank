package me.dfdx.metarank.store

import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import me.dfdx.metarank.aggregation.Scope
import me.dfdx.metarank.model.Featurespace
import me.dfdx.metarank.store.HeapStore.{HeapMapStore, HeapValueStore}
import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}

class HeapStore(fs: Featurespace) extends Store {
  val byteCache = Scaffeine().build[String, Any]()

  override def kv[K, V](desc: StateDescriptor.MapStateDescriptor[K, V], scope: Scope): MapState[K, V] =
    new HeapMapStore(fs, scope, byteCache, desc.kc)

  override def value[T](desc: StateDescriptor.ValueStateDescriptor[T], scope: Scope): ValueState[T] =
    new HeapValueStore[T](fs, scope, byteCache)
}

object HeapStore {
  def apply(fs: Featurespace) = new HeapBytesStore(fs)
  class HeapMapStore[K, V](fs: Featurespace, scope: Scope, cache: Cache[String, Any], kc: KeyCodec[K])
      extends MapState[K, V] {
    override def get(key: K): IO[Option[V]] = IO {
      val kstr = s"${Scope.write(fs, scope)}/${kc.write(key)}"
      cache.getIfPresent(kstr).map(_.asInstanceOf[V])
    }

    override def put(key: K, value: V): IO[Unit] = IO {
      val kstr = s"${Scope.write(fs, scope)}/${kc.write(key)}"
      cache.put(kstr, value)
    }

    override def delete(key: K): IO[Unit] = IO {
      val kstr = s"${Scope.write(fs, scope)}/${kc.write(key)}"
      cache.invalidate(kstr)
    }
  }

  class HeapValueStore[T](fs: Featurespace, scope: Scope, cache: Cache[String, Any]) extends ValueState[T] {
    override def get(): IO[Option[T]] = IO {
      cache.getIfPresent(Scope.write(fs, scope)).map(_.asInstanceOf[T])
    }

    override def put(value: T): IO[Unit] = IO {
      cache.put(Scope.write(fs, scope), value)
    }

    override def delete(): IO[Unit] = IO {
      cache.invalidate(Scope.write(fs, scope))
    }
  }

}
