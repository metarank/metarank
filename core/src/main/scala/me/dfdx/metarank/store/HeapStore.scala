package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.tracker.Tracker
import me.dfdx.metarank.tracker.state.State

class HeapStore extends Store {
  val byteCache = Scaffeine().build[String, Array[Byte]]()

  override def load[T <: State](tracker: String, scope: Tracker.Scope)(implicit
      reader: State.Reader[T]
  ): IO[Option[T]] = IO {
    byteCache
      .getIfPresent(key(tracker, scope))
      .map(bytes => reader.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def save[T <: State](tracker: String, scope: Tracker.Scope, value: T)(implicit
      writer: State.Writer[T]
  ): IO[Unit] = IO {
    val buffer = new ByteArrayOutputStream()
    writer.write(value, new DataOutputStream(buffer))
    byteCache.put(key(tracker, scope), buffer.toByteArray)
  }
}
