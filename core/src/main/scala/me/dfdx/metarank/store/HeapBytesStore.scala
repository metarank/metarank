package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.State
import me.dfdx.metarank.store.state.State.ValueState

class HeapBytesStore extends Store {
  val byteCache = Scaffeine().build[String, Array[Byte]]()

  override def get[T](desc: ValueState[T], scope: Aggregation.Scope): IO[Option[T]] = IO {
    byteCache
      .getIfPresent(keystr(desc, scope))
      .map(bytes => desc.codec.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def put[T](desc: ValueState[T], scope: Aggregation.Scope, value: T): IO[Unit] = IO {
    val buffer = new ByteArrayOutputStream()
    desc.codec.write(value, new DataOutputStream(buffer))
    byteCache.put(keystr(desc, scope), buffer.toByteArray)
  }

  override def get[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K): IO[Option[V]] = IO {
    byteCache
      .getIfPresent(keystr(desc, scope, key)(desc.kc))
      .map(bytes => desc.vc.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def put[K, V](desc: State.MapState[K, V], scope: Aggregation.Scope, key: K, value: V): IO[Unit] = IO {
    val buffer = new ByteArrayOutputStream()
    desc.vc.write(value, new DataOutputStream(buffer))
    byteCache.put(keystr(desc, scope, key)(desc.kc), buffer.toByteArray)
  }
}

object HeapBytesStore {
  def apply() = new HeapBytesStore()
}
