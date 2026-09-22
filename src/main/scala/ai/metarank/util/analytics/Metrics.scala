package ai.metarank.util.analytics

import io.prometheus.metrics.core.metrics.{Counter, Summary}

object Metrics {
  lazy val requests =
    Counter.builder().name("metarank_rank_requests").help("Number of /rank requests").labelNames("model").register()

  lazy val events =
    Counter.builder().name("metarank_feedback_events").help("Number of feedback events received").register()

  lazy val requestLatency = Summary
    .builder()
    .name("metarank_rank_latency_seconds")
    .help("rank endpoint latency")
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
