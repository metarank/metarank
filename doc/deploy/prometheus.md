# Metrics

Metarank exports a set of internal metrics you can use to monitor its health. See the [`/metrics`](../api.md#prometheus-metrics) endpoint description for details on how to access them.

## Application metrics

All application metrics have a common `metarank_` prefix:

1. `metarank_rank_requests`: counter, number of requests received by the `/rank` endpoint. This metric also counts requests by model name.

```
metarank_rank_requests_total{model="model_name",} 5.0
```

2. `metarank_feedback_events`: counter, number of feedback events received both from API and any other connector (like kafka/pulsar/kinesis). 

```
metarank_feedback_events_total 58441.0
```

3. `metarank_rank_latency_seconds`, histogram, latency distribution for `/rank` requests, scoped by a model. Percentiles tracked: 50%, 80%, 90%, 95%, 98%, 99%.

```
metarank_rank_latency_seconds{model="xgboost",quantile="0.5",} 0.011451508
metarank_rank_latency_seconds{model="xgboost",quantile="0.8",} 0.014340056
metarank_rank_latency_seconds{model="xgboost",quantile="0.9",} 0.119447575
metarank_rank_latency_seconds{model="xgboost",quantile="0.95",} 0.119447575
metarank_rank_latency_seconds{model="xgboost",quantile="0.98",} 0.119447575
metarank_rank_latency_seconds{model="xgboost",quantile="0.99",} 0.119447575
metarank_rank_latency_seconds_count{model="xgboost",} 5.0
metarank_rank_latency_seconds_sum{model="xgboost",} 0.16446094099999997
```

## JVM metrics

Metarank also exports a set of [default JVM metrics](https://github.com/prometheus/client_java/blob/main/simpleclient_hotspot/src/main/java/io/prometheus/client/hotspot/DefaultExports.java), related to buffers, classloaders, GC, allocation and threadpools.

All the JVM metrics have common `jvm_` prefix.

## Grafana dashboard

coming soon.
