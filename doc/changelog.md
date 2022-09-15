# Changelog

In a human-readable format. For a technical changelog for robots, see [github releases page](https://github.com/metarank/metarank/releases).
Check our [blog](https://blog.metarank.ai) for more detailed updates.

## 0.5.3
Highlights of this release are:
* added [historical data sorting](/doc/cli#historical-data-sorting) sub-command
* added [core.clickthrough.maxSessionLength and core.clickthrough.maxParallelSessions](/doc/configuration/overview.md#click-through-joining) parameters to improve memory consumption

## 0.5.2.
Highlights of this release are:
* added [analytics](/doc/configuration/overview.md#anonymous-usage-analytics) and [error tracking](/doc/configuration/overview.md#error-logging)

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