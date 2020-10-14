package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.{Aggregation, Scope}
import me.dfdx.metarank.config.StoreConfig
import me.dfdx.metarank.model.Featurespace
import me.dfdx.metarank.store.state.{MapState, StateDescriptor, ValueState}
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}

trait Store {
  def value[T](desc: ValueStateDescriptor[T], scope: Scope): ValueState[T]
  def kv[K, V](desc: MapStateDescriptor[K, V], scope: Scope): MapState[K, V]
}

object Store {
  case class StoreInitError(msg: String) extends Throwable(msg)
  def fromConfig(store: StoreConfig, fs: Featurespace): Store = store match {
    case StoreConfig.HeapBytesStoreConfig()       => HeapBytesStore(fs)
    case StoreConfig.HeapStoreConfig()            => HeapStore(fs)
    case StoreConfig.RedisStoreConfig(host, port) => RedisStore(fs, host, port)
    case StoreConfig.NullStoreConfig()            => NullStore
  }
}
