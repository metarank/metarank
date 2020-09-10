package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.state.State

class HeapStore extends Store {
  val byteCache = Scaffeine().build[String, Array[Byte]]()

  override def load[T <: State](key: String)(implicit reader: State.Reader[T]): IO[Option[T]] = IO {
    byteCache.getIfPresent(key).map(bytes => reader.read(new DataInputStream(new ByteArrayInputStream(bytes))))
  }

  override def save[T <: State](key: String, value: T)(implicit writer: State.Writer[T]): IO[Unit] = IO {
    val buffer = new ByteArrayOutputStream()
    writer.write(value, new DataOutputStream(buffer))
    byteCache.put(key, buffer.toByteArray)
  }
}
