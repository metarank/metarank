# Changelog

In a human-readable format. For a technical changelog for robots, see [github releases page](https://github.com/metarank/metarank/releases).
Check our [blog](https://blog.metarank.ai) for more detailed updates.

## 0.7.5

* [Unbiased LTR support](configuration/supported-ranking-models.md#xgboost-and-lightgbm-backend-options)
* [Train/test splitting strategy support](configuration/supported-ranking-models.md#traintest-splitting-strategies)

## 0.7.4

* support for rocksdb-backed file storage

## 0.7.3

* a bugfix release

## 0.7.2
* Support for kv-granular Redis TTLs
* Support HF tokenizers for biencoders: now you can run a multi-lingual E5 model in Metarank!

## 0.7.1

* `/inference`: [Inference API](api.md#inference-with-llms) to expose bi- and cross-encoders. 

## 0.7.0

* Support for [LLM embedding-based content recommendations](configuration/recommendations/semantic.md)
* `field_match` support for [BM25](configuration/features/text.md#bm25-score)
* `field_match` support for [LLM bi-encoders](configuration/features/text.md#llm-bi-encoders)
* `field_match` support for [LLM cross-encoders](configuration/features/text.md#llm-cross-encoders)
* Relevance judgments can now also [be explicit](event-schema.md#ranking-event)


## 0.6.4

* a minor bugfix release

## 0.6.3

* [diversity](configuration/features/diversity.md) feature extractor
* [scoped rate](configuration/features/counters.md#field-scoped-rates) feature
* fixed an important bug with dataset preparation (symptom: NDCG reported by the booster was higher than NDCG computed after the training) - prediction quality should go up a lot.

## 0.6.2

* print NDCG before and after reranking
* print statistics for mem usage after training

## 0.6.1

* fix for crash when using file-based clickthrough store

## 0.6.0

Upgrading: note that redis state format has a non backwards compatible change, so you need to reimport the data when upgrading.

* [recommendations](configuration/recommendations.md) support for similar and trending items models.
* Local caching for state, the import should be 2x-3x faster.

## 0.5.16

* [expose](deploy/docker.md#memory) `JAVA_OPTS` env variable to control JVM heap size.
* fix bug for a case when there is a click on a non-existent item.

## 0.5.15

* `cache.maxSize` for redis now disables client-side caching altogether. Makes Metarank compatible with GCP Memstore Redis.
* fixed mem leak in clickthrough joining buffer.
* lower mem allocation pressure in interacted_with feature.

## 0.5.14

* interacted_with feature now supports string[] fields
* fixed a notorious bug with local-file click-through store. 

## 0.5.13

* XGBoost LambdaMART impl now supports categorical encoding.
* [Event selector support](configuration/supported-ranking-models.md#event-selectors) when serving multiple models.
* [Clickthrough storage configuration](configuration/overview.md#training) for storing clickthrough data.

## 0.5.12

* Redis AUTH override with [env vars](configuration/persistence.md#redis-persistence)
* Prometheus `/metrics` [endpoint](deploy/prometheus.md)
* Per-item [ranking fields](event-schema.md#ranking-event)

## 0.5.11

* linux/aarch64 support, so docker images on Mac M1 are OK.
* --split option for CLI with [multiple splitting strategies](cli.md#training-the-model)
* a ton of bugfixes

## 0.5.10

* Redis [TLS support](configuration/persistence.md#tls-support)
* Redis [timeout configuration](configuration/overview.md#persistence)

## 0.5.9

* [`interacted_with`](configuration/features/user-session.md#interacted-with) feature now has much less overhead in Redis, and supports multiple fields in a single visitor profile.
* [click-through events now can be stored in a file](configuration/overview.md#training), and not inside Redis, also reducing the overall costs of running Metarank
* it is now possible to [export lightgbm/xgboost-compatible](cli.md#dataset-export) datasets for further hyper-parameter optimization.

## 0.5.8

* bugfix: add explicit sync on /feedback api call
* bugfix: config decoding error for field_match over terms 
* bugfix: version detect within docker was broken
* bugfix: issue with improper iface being bound in docker

## 0.5.7

* [Request latency benchmark](performance.md), with ballpark estimations useful for resource planning.
* [Vector feature extractor](configuration/features/scalar.md#vector-extractor) with reducer and autofeature support.
* [Redis AUTH support](configuration/persistence.md#redis-persistence) which is common on managed Redis setups. 

## 0.5.6

* [Binary state serialization format](configuration/persistence.md#state-encoding-formats), which is 2x faster and 4x more compact than JSON
* [Multi-arch docker images](deploy/docker.md), so metarank can now be run natively on Mac M1/M2.
* [Kubernetes Helm chart](deploy/kubernetes.md) and an official guide on how to do production deployment on k8s.
* [Training dataset export](cli.md#training-the-model) for further hyper-parameter tuning.

## 0.5.5
Notable features:
* [Rate normalization](configuration/features/counters.md#rate-normalization) support, so having 1 click over 2 
impressions is not resulting in a 50% CTR anymore.
* [Position de-biasing](configuration/features/relevancy.md#position) based on a dynamic position feature.

## 0.5.4
Most notable improvements:
* [AutoML style feature engineering](howto/autofeature.md) based on an existing dataset

## 0.5.3
Highlights of this release are:
* added [historical data sorting](cli#historical-data-sorting) sub-command
* added [core.clickthrough.maxSessionLength and core.clickthrough.maxParallelSessions](configuration/overview.md#click-through-joining) parameters to improve memory consumption

## 0.5.2.
Highlights of this release are:
* added [analytics](configuration/overview.md#anonymous-usage-analytics) and [error tracking](configuration/overview.md#error-logging)

## 0.5.1

Highlights of this release are:
* Flink is rermoved. As a result only `memory` and `redis` [persistance](configuration/persistence.md) modes are supported now.
* [Configuration file](configuration/sample-config.yml) now has updated structure and is not compatible with previous format.
* CLI is updated, most of the options are moved to [configuration](configuration/overview.md).
  * We have updated the [`validate`](cli.md#validation) mode of the CLI, so you can use it to validate your data and configuration.

## 0.4.0

Highlights of this release are:
* Kubernetes support: now it's possible to have a production-ready metarank deployment in K8S
* Kinesis source: on par with Kafka and Pulsar
* Custom connector options pass-through

### Kunernetes support

Metarank is a multi-stage and multi-component system, and now it's possible to get it deployed
in minutes inside a Kubernetes cluster:
* Inference API is just a regular [Deployment](https://github.com/metarank/metarank/blob/master/deploy/kubernetes/deployment.yaml)
* Bootstrap, Upload and Update jobs can be run both locally (to simplify things up for small datasets) and
inside the cluster in a distributed mode.
* Job mabagement is done with [flink-kubernetes-operator](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/)

See [this doc section](https://docs.metarank.ai/deployment/kubernetes) for details.

### Kinesis source

[Kinesis Streams](https://aws.amazon.com/kinesis/data-streams/) can also be used as an event source.
It still has a couple of drawbacks compared with Kafka/Pulsar, for example, due to max 7 day data retention
it cannot be effectively used as a permanent storage of historical events. But it's still possible to
pair it with a AWS Firehose writing events to S3, so realtime events are coming from Kinesis, and historical
events are offloaded to S3.

Check out [the corresponding part of docs](https://docs.metarank.ai/introduction/configuration/data-sources#aws-kinesis-streams) for details and examples.

### Custom connector options pass-through

As we're using Flink's connector library for pulling data from Kafka/Kinesis/Pulsar, there is a ton of
custom options you can tune for each connector. It's impossible to expose all of them directly,
so now in connector config there is a free-form `options` section, allowing to set any supported
option for the underlying connector.

Example to force Kinesis use EFO consumer:
```yaml
type: kinesis
topic: events
region: us-east-1
offset: latest
options: 
  flink.stream.recordpublisher: EFO 
  flink.stream.efo.consumername: metarank 
```

See [this doc section](https://docs.metarank.ai/introduction/03_configuration/data-sources#common-options-for-bootstrapping-connectors) for details.