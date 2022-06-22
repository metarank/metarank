# Snowplow integration

![snowplow logo](../img/snowplow_logo.png)

Metarank can be integrated into the existing [Snowplow Analytics](https://snowplowanalytics.com/) setup. With this approach:
* existing [Snowplow Analytics SDK](https://docs.snowplowanalytics.com/docs/modeling-your-data/analytics-sdk/)
can be used for application instrumentation.
* Metarank will use Snowplow's enriched event stream as a source of events.

## Typical Snowplow architecture

Typical Snowplow Analytics setup consists of these parts:
* Using Analytics SDK, front-end emits a clickstream telemetry to the Collector
* Collector writes all incoming events into the raw stream
* Enrich validates these events according to the predefined schemas from the Schema Registry
* Validated and enriched events then written to the enriched stream
* Enriched events are delivered to the Analytics DB

Metarank exposes a set of Snowplow-compatible event schemas, and can read events
directly from the enriched stream, like shown on the diagram below:

![snowplow typical setup](../img/snowplow-setup.png)

###  Schema registry

All incoming raw events have a strict JSON schema, consisting of the following parts:
* predefined fields according to the [Snowplow Tracker Protocol](https://docs.snowplowanalytics.com/docs/collecting-data/collecting-from-own-applications/snowplow-tracker-protocol/)
* unstructured payload with user-defined schema
* multiple context payloads with user-defined schemas

These user-defined schemas are pulled from the [Iglu Registry](https://docs.snowplowanalytics.com/docs/pipeline-components-and-applications/iglu/), 
and these schemas are just normal [JSON Schema](https://json-schema.org/specification.html) definitions,
describing the payload structure.

There are four different Metarank event types with the corresponding schemas:
1. `ai.metarank/item/1-0-0`: [item metadata event](https://github.com/metarank/metarank-snowplow/blob/master/schemas/ai.metarank/item/1-0-0)
2. `ai.metarank/user/1-0-0`: [user metadata event](https://github.com/metarank/metarank-snowplow/blob/master/schemas/ai.metarank/user/1-0-0)
3. `ai.metarank/ranking/1-0-0`: [ranking event](https://github.com/metarank/metarank-snowplow/blob/master/schemas/ai.metarank/item/1-0-0)
4. `ai.metarank/interaction/1-0-0`: [interaction event](https://github.com/metarank/metarank-snowplow/blob/master/schemas/ai.metarank/interaction/1-0-0)

These schemas are describing native [Metarank event types](../event-schema.md) without any modifications. 
Check out [github.com/metarank/metarank-snowplow](https://github.com/metarank/metarank-snowplow) repo for more details on Metarank schemas.

### Stream transport types

Snowplow supports [multiple streaming platforms](https://docs.snowplowanalytics.com/docs/pipeline-components-and-applications/stream-collector/setup/) for event delivery:
* [AWS Kinesis](https://aws.amazon.com/kinesis/): supported by Metarank
* [GCP Pubsub](https://cloud.google.com/pubsub): support is [planned in future](https://github.com/metarank/metarank/issues/477)
* [Kafka](https://kafka.apache.org/): supported by Metarank
* [NSQ](https://nsq.io/): not supported

## Setting up event tracking on frontend

todo

## Installing ai.metarank schemas

Metarank schemas are available on a public Iglu server on https://iglu.metarank.ai. To use it,
add the following snippet to the `resolver.json` snowplow-enrich config file:
```json
{
  "schema": "iglu:com.snowplowanalytics.iglu/resolver-config/jsonschema/1-0-1",
  "data": {
    "cacheSize": 500,
    "repositories": [
      {
        "name": "Metarank",
        "priority": 0,
        "vendorPrefixes": [ "ai.metarank" ],
        "connection": {
          "http": {
            "uri": "https://iglu.metarank.ai"
          }
        }
      }
    ]
  }
}
```

Both `http` and `https` schemas are supported, but `https` is recommended.

## Connecting Metarank to the enriched stream

## 