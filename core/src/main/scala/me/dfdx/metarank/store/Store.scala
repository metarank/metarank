package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}

trait Store {
  def value[T](desc: ValueStateDescriptor[T], scope: Aggregation.Scope): ValueState[T]
  def kv[K, V](desc: MapStateDescriptor[K, V], scope: Aggregation.Scope): MapState[K, V]
}
