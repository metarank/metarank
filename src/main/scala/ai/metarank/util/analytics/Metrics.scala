package ai.metarank.util.analytics

import io.prometheus.client.{Counter, Gauge, Histogram, Summary}

object Metrics {
  lazy val requests =
    Counter.build("metarank_rank_requests", "Number of /rank requests").labelNames("model").register()

  lazy val events = Counter.build("metarank_feedback_events", "Number of feedback events received").register()

  lazy val requestLatency = Summary
    .build("metarank_rank_latency_seconds", "rank endpoint latency")
    .labelNames("model")
    .maxAgeSeconds(600)
    .quantile(0.5, 0.01)
    .quantile(0.8, 0.01)
    .quantile(0.9, 0.01)
    .quantile(0.95, 0.01)
    .quantile(0.98, 0.001)
    .quantile(0.99, 0.001)
    .register()

}
