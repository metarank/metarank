package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.aggregation.state.State
import me.dfdx.metarank.aggregation.state.State.{Reader, Writer}

class HeapStore extends Store {
  val byteCache = Scaffeine().build[String, State]()

  override def load[T <: State: Reader](tracker: Aggregation, scope: Aggregation.Scope): IO[Option[T]] = IO {
    byteCache
      .getIfPresent(key(tracker, scope))
      .map(value => value.asInstanceOf[T])
  }

  override def save[T <: State: Writer](tracker: Aggregation, scope: Aggregation.Scope, value: T): IO[Unit] = IO {
    byteCache.put(key(tracker, scope), value)
  }
}
