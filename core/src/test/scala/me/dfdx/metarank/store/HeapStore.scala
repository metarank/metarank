package me.dfdx.metarank.store

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import cats.effect.IO
import com.github.blemale.scaffeine.Scaffeine
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.StateDescriptor
//import me.dfdx.metarank.store.state.codec.StateDescriptor.ValueStateDescriptor

//class HeapStore extends Store {
//  val byteCache = Scaffeine().build[String, Any]()
//
//  override def get[T](desc: ValueStateDescriptor[T], scope: Aggregation.Scope): IO[Option[T]] = IO {
//    byteCache
//      .getIfPresent(keystr(desc, scope))
//      .map(value => value.asInstanceOf[T])
//  }
//
//  override def put[T](desc: ValueStateDescriptor[T], scope: Aggregation.Scope, value: T): IO[Unit] = IO {
//    byteCache.put(keystr(desc, scope), value)
//  }
//
//  override def get[K, V](
//      desc: StateDescriptor.MapStateDescriptor[K, V],
//      scope: Aggregation.Scope,
//      key: K
//  ): IO[Option[V]] = ???
//
//  override def put[K, V](
//      desc: StateDescriptor.MapStateDescriptor[K, V],
//      scope: Aggregation.Scope,
//      key: K,
//      value: V
//  ): IO[Unit] = ???
//}
