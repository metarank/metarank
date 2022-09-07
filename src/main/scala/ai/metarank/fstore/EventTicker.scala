package ai.metarank.fstore

import ai.metarank.model.Event
import com.github.benmanes.caffeine.cache.Ticker

class EventTicker extends Ticker {
  var last = 0L
  def tick(event: Event) = {
    last = event.timestamp.ts * 1000000
  }
  override def read(): Long = last
}
