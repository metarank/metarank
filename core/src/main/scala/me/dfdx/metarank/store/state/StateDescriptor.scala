package me.dfdx.metarank.store.state

import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}

sealed trait StateDescriptor[T] {
  def name: String
  def default: T
}

object StateDescriptor {
  case class ValueStateDescriptor[T](name: String, default: T)(implicit val codec: Codec[T]) extends StateDescriptor[T]
  case class MapStateDescriptor[K, V](name: String, default: V)(implicit val kc: KeyCodec[K], val vc: Codec[V])
      extends StateDescriptor[V]

}
