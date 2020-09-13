package me.dfdx.metarank.store

import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.aggregation.state.CircularReservoir
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HeapBytesStoreTest extends StoreTestSuite {
  override lazy val store = new HeapBytesStore()
}
