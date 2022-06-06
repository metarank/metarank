## Data sources

Metarank has two data processing stages:
* *bootstrapping*: consume historical visitor interaction events and produce latest point-in-time snapshot of the system 
and all the ML features. It's a one-time batch offline job, used for generating or updating a ML model.
* *inference*: loads the snapshot from the bootstrapping phase and continues processing live events that come realtime. It's
a continuous realtime online job.

Both of these stages require event data stream and Metarank provides several sources that Metarank can use to read these events.

## Bootstrapping data sources

### Common options for bootstrapping connectors

All supported connectors have some shared options:
* offset: a time window in which events are read
  * `earliest` - start from the first stored message in the topic
  * `latest` - consume only events that came recently (after Metarank connection)
  * `ts=\<timestamp\>` - start from a specific absolute timestamp in the past
  * `last=\<duration\>` - consume only events that happened within a defined relative duration (duration supports the
  following patterns: `1s`, `1m`, `1h`, `1d`)
* options (for kinesis/pulsar/kafka connectors): raw flink custom connector options to override default behavior:
  * kafka: [KafkaSourceOptions](https://github.com/apache/flink/blob/master/flink-connectors/flink-connector-kafka/src/main/java/org/apache/flink/connector/kafka/source/KafkaSourceOptions.java)
  and [ConsumerConfig](https://kafka.apache.org/24/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html)
  * pulsar: [PulsarOptions](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/pulsar/#source-configurable-options)
  * kinesis: [AWSConfigConstants](https://github.com/apache/flink/blob/master/flink-connectors/flink-connector-aws-base/src/main/java/org/apache/flink/connector/aws/config/AWSConfigConstants.java) and
  [ConsumerConfigConstants](https://github.com/apache/flink/blob/master/flink-connectors/flink-connector-kinesis/src/main/java/org/apache/flink/streaming/connectors/kinesis/config/ConsumerConfigConstants.java)

An example of custom connector options:
```yaml
type: kinesis
options:
  flink.stream.efo.consumername: helloworld
  flink.stream.describe.backoff.base: '1000'
```

Please note that custom options are expected to be a string->string mapping, so numeric
options should be quoted according to YAML spec.

### File

Config file definition example:
```yaml
type: file
path: file:///ranklens/events/
offset: earliest|latest|ts=<unixtime>|last=<duration>
```

The *path* parameter supports the following possible access scheme formats:
* `file://` - read files from local directory recursively. Won't work with distributed deployments of Metarank, as 
`file://` prefix assumes a node-local access to the filesystem.
* `s3://` - read files from an S3 bucket.

For `file://` access scheme, note that according to the [RFC 1738](https://www.ietf.org/rfc/rfc1738.txt) file URL 
pointing to the localhost, may omit the `localhost` hostname and then must contain **THREE** slashes after the scheme. 
So a valid file URL example is `file:///data/events` (`file://data/events` means that `data` is a hostname and not a local file location).

So file URLs in Metarank should always use triple-slash prefix..

#### S3-like data storage
Check out [AWS S3 source documentation](deploy/aws-s3.md) for reading data from S3-like sources.


### Apache Kafka

[Apache Kafka](https://kafka.apache.org/) is an open source distributed event streaming platform. 

If you already use Kafka in your project, Metarank can connect to an existing Kafka topic to read incoming and stored events both for bootstraping (with an offset set to some time in the past)
and inference (when offset is set to `latest`) stages.

Kafka connector is configured in the following way:

```yaml
type: kafka
brokers: [broker1, broker2]
topic: events
groupId: metarank
offset: earliest|latest|ts=<unixtime>|last=<duration>
options: # optional flink connector raw parameters, map<string,string>
```

### Apache Pulsar

[Apache Pulsar](https://pulsar.apache.org/) is an open source distributed messaging and streaming platform.

If you already use Pulsar in your project, Metarank can connect to an existing Pulsar topic to read incoming and stored events both for bootstraping (with an offset set to some time in the past)
and inference (when offset is set to `latest`) stages.

Metarank supports Pulsar *2.8+*, but using *2.9+* is recommended. You can check [Apache Flink docs](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/pulsar/)
for a description of how the underlying connector works.

Pulsar connector is configured in the following way:
```yaml
type: pulsar
serviceUrl: <pulsar service URL>
adminUrl: <pulsar service HTTP admin URL>
topic: events
subscriptionName: metarank
subscriptionType: exclusive # options are exclusive, shared, failover
offset: earliest|latest|ts=<unixtime>|last=<duration>
options: # optional flink connector parameters, map<string,string>
```

## Inference data sources

Both [Pulsar](#apache-pulsar) and [Kafka](#apache-kafka) datasources can be used in the inference process as a source of real-time events. 
We recommend setting `offset` parameter to `latest` (or another value not too far back in time).

### REST API

It's possible to ingest real-time feedback events directly using the REST API of Metarank. Under the hood, the API has 
two routes:
* `POST /feedback` - push feedback events to the internal buffer
* `GET /feedback` - pull all the collected feedback events from the buffer.

You can read more about Metarank REST API in the [API Documentation](api_schema.md);

Feedback processing job periodically polls the `/feedback` endpoint and processes all the events it emits. In other words, 
a REST API feedback ingestion has no persistence (restarted API causes data loss) and no failover (glitch in feedback processing
job means data loss). But it requires no external systems and is a wonderful tool for local dev runs in a playground.

REST connector is configured in the following way:
```yaml
    type: rest
    bufferSize: 1000 # optional, default 10000
    host: localhost # hostname of metarank API
    port: 8080 # port of metarank API
```
