package me.dfdx.metarank.store.state

import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}

sealed trait StateDescriptor {
  def name: String
}

object StateDescriptor {
  case class ValueStateDescriptor[T](name: String)(implicit val codec: Codec[T]) extends StateDescriptor
  case class MapStateDescriptor[K, V](name: String)(implicit val kc: KeyCodec[K], val vc: Codec[V])
      extends StateDescriptor

}
