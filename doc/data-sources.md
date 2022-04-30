## Data sources

Metarank has two data processing stages:
* bootstrapping: consume historical visitor interaction events and produce latest point-in-time snapshot of the system 
and all the ML features. It's an one-time batch offline job.
* inference: loads the snapshot from the bootstrapping phase and continue processing live events coming in realtime. It's
a continuous realtime online job.

## Bootstrapping data sources

### File

Config file definition example:
```yaml
type: file
path: file:///ranklens/events/
```

The actual path supports the following possible access scheme formats:
* `file://` - read files from local directory recursively. Won't work with distributed deployments of Metarank, as 
`file://` prefix assumes a node-local access to the filesystem.
* `s3://` - read files from an S3 bucket.

For `file://` access scheme, note that according to the [RFC 1738](https://www.ietf.org/rfc/rfc1738.txt) file URL 
pointing to the localhost, may omit the `localhost` hostname and then must contain THREE slashes after the scheme. 
So valid file URL example is `file:///data/events`. And `file://data/events` should mean that `data` is a hostname.
So file URLs in Metarank should always use triple-slash.

For S3 access, you need to pass the credentials to the underlying connector using the following env variables:
* AWS_ACCESS_KEY_ID - key id
* AWS_SECRET_ACCESS_KEY - key secret


### Kafka

Both Kafka and Pulsar can be used for a persistent event storage within a predefined retention. So in practice you
can use the same system both for bootstrapping (with offset set to some moment back in time) and for inference (when
offset is set to latest).

Kafka connector configured in the following way:

```yaml
type: kafka
brokers: [broker1, broker2]
topic: events
groupId: metarank
offset: earliest|latest|ts=<unixtime>|last=<duration>
```
Offset options are:
* earliest - start from the first stored message in the topic
* latest - consume only events that came recently (after we connected)
* ts=\<timestamp\> - start from a specific absolute timestamp in the past
* last=\<duration\> - consume only events happened within the defined relative duration (duration supports the 
following patterns: 1s, 1m, 1h, 1d)

### Pulsar

Metarank supports pulsar 2.8+, but using 2.9+ is recommended. See the [Apache Flink docs](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/pulsar/)
for the description on how the underlying connector works.

Pulsar connector configured in the following way:
```yaml
type: pulsar
serviceUrl: <pulsar service URL>
adminUrl: <pulsar service HTTP admin URL>
topic: events
subscriptionName: metarank
subscriptionType: exclusive # options are exclusive, shared, failover
offset: earliest|latest|ts=<unixtime>|last=<duration>
```

## Inference data sources

Both Pulsar and Kafka datasources can be used in the inference process as a source of real-time events. 
It's highly recommended to set starting offset to some value not being too far away back in time (`latest` is a good option)

### REST

It's possible to ingest real-time feedback events directly using the REST API of Metarank. Under the hood, the API has 
two routes:
* `POST /feedback` - push feedback events to the internal buffer
* `GET /feedback` - pull all the collected feedback events from the buffer.

Feedback processing job will periodically poll the /feedback endpoint and process all the events it emits. In other words, 
a REST API feedback ingestion has no persistence (restarted API causes data loss) and no failover (glitch in feedback processing
job means data loss). But it requires no external systems and is wonderful for a local dev runs in a playground.

REST connector configured in the following way:
```yaml
    type: rest
    bufferSize: 1000 # optional, default 10000
    host: localhost # hostname of metarank API
    port: 8080 # port of metarank API
```
