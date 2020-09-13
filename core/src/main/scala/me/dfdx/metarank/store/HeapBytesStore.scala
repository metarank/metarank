package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.aggregation.state.State

class HeapBytesStore extends Store {
  val byteCache = Scaffeine().build[String, Array[Byte]]()

  override def load[T <: State](tracker: Aggregation, scope: Aggregation.Scope)(implicit
      reader: State.Reader[T]
  ): IO[Option[T]] = IO {
    byteCache
      .getIfPresent(key(tracker, scope))
      .map(bytes => reader.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def save[T <: State](tracker: Aggregation, scope: Aggregation.Scope, value: T)(implicit
      writer: State.Writer[T]
  ): IO[Unit] = IO {
    val buffer = new ByteArrayOutputStream()
    writer.write(value, new DataOutputStream(buffer))
    byteCache.put(key(tracker, scope), buffer.toByteArray)
  }
}

object HeapBytesStore {
  def apply() = new HeapBytesStore()
}
