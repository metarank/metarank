# Data sources

Metarank has two data processing stages:
* *import*: consume historical visitor interaction events and produce latest point-in-time snapshot of the system 
and all the ML features. 
* *inference*: continues processing live events that come realtime.

An overview diagram of event flow during inference/bootstrap is shown below:
![bootstrap and inference event flow](img/connectors.png)

Metarank supports the following list of connectors:

| Name                                                       | Import                 | Inference |
|------------------------------------------------------------|------------------------|-----------|
| [Apache Kafka](data-sources.md#apache-kafka)               | yes                    | yes       |
| [Apache Pulsar](data-sources.md#apache-pulsar)             | yes                    | yes       |
| [AWS Kinesis Streams](data-sources.md#aws-kinesis-streams) | yes, but actually no * | yes       |
| [RESTful API](data-sources.md#rest-api)                    | yes                    | yes       |
| [Files](data-sources.md#files)                             | yes                    | no        |

`*` AWS Kinesis has a strict max 7 days retention period, so if you want to store more than 7
days of historical clickthrough events, choose something else (for example, add AWS 
Kinesis Firehose to write events from Kinesis topic to S3 files to pick them with `Files` 
connector).

## Common options for bootstrapping connectors

All supported connectors have some shared options:
* offset: a time window in which events are read
  * `earliest` - start from the first stored message in the topic
  * `latest` - consume only events that came recently (after Metarank connection)
  * `ts=<timestamp>` - start from a specific absolute timestamp in the past
  * `last=<duration>` - consume only events that happened within a defined relative duration (duration supports the
  following patterns: `1s`, `1m`, `1h`, `1d`)
* format: event record encoding format, possible options:
  * `json`: Both Json-line (newline separated records) and Json-array (`[{event:1}, {event:2}]`) formats are supported.
  * `snowplow:tsv|snowplow:json` - Snowplow-native format, see [Snowplow integration](integrations/snowplow.md) for details
  on how to set it up

### File

Config file definition example:
```yaml
type: file
path: /home/user/ranklens/events/
offset: earliest|latest|ts=<unixtime>|last=<duration>
format: <json|snowplow:tsv|snowplow:json>
```

The *path* parameter is a node-local file or directory with the input dataset. 

The `file` data source supports:
* compression, auto-detected based on file extension: ZStandard and GZip are supported
* directories with multiple input files. Metarank sorts files by their modification date to read them in proper sequence.


### Apache Kafka

[Apache Kafka](https://kafka.apache.org/) is an open source distributed event streaming platform. 

If you already use Kafka in your project, Metarank can connect to an existing Kafka topic to read incoming and stored 
events both for import (with an offset set to some time in the past) and inference (when offset is set to `latest`) stages.

Kafka connector is configured in the following way:

```yaml
type: kafka
brokers: [broker1, broker2]
topic: events
groupId: metarank
offset: earliest|latest|ts=<unixtime>|last=<duration>
format: <json|snowplow:tsv|snowplow:json>
options: # optional connector raw parameters, map<string,string>
```

Extra connector options can be taken from 
[ConsumerConfig](https://kafka.apache.org/32/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html) in the 
following way: 

```yaml
type: kafka
options:
  client.id: 'helloworld'
  enable.auto.commit: 'false'
```

### Apache Pulsar

[Apache Pulsar](https://pulsar.apache.org/) is an open source distributed messaging and streaming platform.

If you already use Pulsar in your project, Metarank can connect to an existing Pulsar topic to read incoming and stored 
events both for import (with an offset set to some time in the past) and inference (when offset is set to `latest`) stages.

Metarank supports Pulsar *2.8+*, but using *2.9+* is recommended. 

Pulsar connector is configured in the following way:
```yaml
type: pulsar
serviceUrl: <pulsar service URL>
adminUrl: <pulsar service HTTP admin URL>
topic: events
subscriptionName: metarank
subscriptionType: exclusive # options are exclusive, shared, failover
offset: earliest|latest|ts=<unixtime>|last=<duration>
format: <json|snowplow:tsv|snowplow:json>
options: # optional connector parameters, map<string,string>
```

Extra connector options can be taken from 
[Configuring Pulsar consumer](https://pulsar.apache.org/docs/client-libraries-java/#configure-consumer) in the
following way:

```yaml
type: pulsar
options:
  receiverQueueSize: 10
  acknowledgementsGroupTimeMicros: 50000
```

### AWS Kinesis Streams

[AWS Kinesis Streams](https://aws.amazon.com/kinesis/) is a fully-managed event streaming platform.
Metarank uses a connector for [Apache Flink](https://flink.apache.org) which is well-maintained and
feature complete.

To configure the connector, use this reference YAML block:
```yaml
type: kinesis
region: us-east-1
topic: events
offset: earliest|latest|ts=<unixtime>|last=<duration>
format: <json|snowplow:tsv|snowplow:json>
options: # optional custom options for Flink connector, map<string,string>
```

Important things to note when using AWS Kinesis connector for bootstrap:
* AWS Kinesis has a hard limit on [max retention time of 7 days](https://docs.aws.amazon.com/streams/latest/dev/service-sizes-and-limits.html).
If you want to store more data there, use AWS Firehose to offload these events to S3 and pick them with the `File` connector.
* AWS limits max throughput per shard to 2Mb/s, so it may take some time to pull large dataset
from kinesis. You may need to consider using an EFO consumer for a dedicated throughput.

#### AWS Authentication

Kinesis source uses a default auth chain from the AWS SDK, so all the possible ways of
AWS SDK authentication methods are supported, but in short:
* Use IAM roles when possible
* add AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY env vars to manually supply the keys.

### REST API

It's possible to ingest real-time feedback events directly using the REST API of Metarank:
* `POST /feedback` - push feedback events to Metarank

The `/feedback` endpoint is always enabled and there is no need to configure it explicitly.

You can read more about Metarank REST API in the [API Documentation](api_schema.md). 
You can bundle multiple events in a single batch using [batch payloads](api_schema.md#feedback), so REST API can be used
for batch dataset import instead of a separate `import` step:

```bash
$ java -jar metarank serve --config conf.yaml
$ curl -XPOST -d @events.json http://localhost:8080/feedback
```