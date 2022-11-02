# Configuration

Metarank YAML config file contains the following sections:
* [Persistence](overview.md#persistence): how feature data is stored
* [Models](overview.md#models): which models should be trained and used in inference
* [Features](overview.md#features): how features are computed from events
* [API](overview.md#api): network options for API
* [Data sources](overview.md#data-sources): where to read events from
* [Core](overview.md#core): service options, like anonymous tracking and error reporting.

See the [sample-config.yml](sample-config.yml) file for a full working example.

## Persistence

The "state" section describes how computed features and models are stored. Check [Persistence configuration](persistence.md) for 
more information. An example persistence conf block with comments:

```yaml
state: # a place to store the feature values for the ML inference and the trained model
    # Local memory
    # A node-local in-memory storage without any persistence. 
    # Feature values and the trained model is stored in-memory.    
    # Suitable only for local testing, as in case of a restart it will loose all the data.
    type: memory

    # Remote redis, with persistence. 
    # Saves the computed features and trained model in a Redis instance.
    # You can use remote or local Redis installation.
    #type: redis
    #host: localhost
    #port: 6369
    #format: binary # optional, default=binary, possible values: json, binary
    
    # Metarank implements several optimization strategies when using Redis: caching and pipelining
    # Check https://docs.metarank.ai/reference/overview/persistence#redis-persistence for more details
    #cache:           # optional
    #  maxSize: 4096  # size of in-memory client-side cache for hot keys, optional, default=4096
    #  ttl: 1h        # how long should key-values should be cached, optional, default=1h

    #pipeline:         # optional
    #  maxSize: 128    # batch write buffer size, optional, default=128
    #  flushPeriod: 1s # buffer flush interval, optional, default=1s

    #auth:                  # optional
    #  user: <username>     # optional when Redis ACL is disabled
    #  password: <password> # required if Redis server is run with requirepass argument

```
## Training

Metarank also computes a click-through data structure, which contains the following bits of information:

* ranking: which items were presented to the visitor
* interactions: what visitor did after seeing the ranking (like clicks, purchases and so on)
* feature values, which were used to produce the ranking in the past.

These click-through events are essential for model training, as they're later translated into the implicit judgement lists for the underlying LambdaMART model:

![Implicit judgements](../img/ltr-table.png)

Metarank has multiple ways of storing these click-throughs with different pros and cons:
* **Redis**: no special configuration needed, it's possible to perform periodic ML model retraining by reading the latest click-through events from it. But it takes quite a lot of RAM and maybe costly in a case when you have millions of click-through events.
* **Local file**: takes much less RAM (as ct's are not stored in redis), but you need to manage the file containing the click-throughs by yourself. 

```yaml
# The optional train section describes how Metarank deals with the 
# training dataset persistence.
# By default, it uses the same way of storing click-through events as set in 
# the state block.
train:
  type: redis

  # Possible values are:
  # - redis: with the same options as the state.redis persistence block.
  # - memory: stores everything in memory
  # - file: dump clickthroughs to the file
  # - discard: drop all click-through events to /dev/null

  # An example file configuration, for a case when you import+train everything 
  # locally, but with a remote Redis to store the state for the inference:

  # type: file
  # path: /path/to/file   # path to a file which will be written during import, 
  #                       # and read during training
  # format: json          # options are: json, binary
```

## Features

This section describes how to map your input events into ML features that Metarank understands.
See [Feature extractors](feature-extractors.md) for an overview of supported types.

```yaml
# These features can be shared between multiple models, so if you have a model A using features 1-2-3 and
# a model B using features 1-2, then all three features will be computed only once. 
# You need to explicitly include a feature in the model configuration for Metarank to use it.
features:
  - name: popularity
    type: number
    scope: item
    source: item.popularity
    # TTL and refresh fields are part of every feature extractor that Metarank supports.
    # The purpose of TTL is to configure data retention period, so in a case when there were no
    # feature updates for a long time, it will eventually be dropped.
    ttl: 60d
    # Refresh parameter is used to downsample the amount of feature updates emitted. For example,
    # there is a window_counter feature extractor, which can be used to count a number of clicks that happened for
    # an item. Incrementing such a counter for a single day is an extremely lightweight operation, but computing
    # window sums is not. As it's not always required to receive up-to-date counter values in ML models,
    # these window sums can be updated only eventually (like once per hour), which improves the throughput a lot
    # (but results in a slightly stale data during the inference process)
    refresh: 1h

  - name: genre
    type: string
    scope: item
    source: item.genres
    values:
      - drama
      - comedy
      - thriller
```

For inspiration, you can use a [ranklens feature configuration](https://github.com/metarank/metarank/blob/master/src/test/resources/ranklens/config.yml)
used for [Metarank demo site](https://demo.metarank.ai).

## Models

The "models" section describes ML models used for personalization. Check [Supported models](supported-ranking-models.md) 
for more information.
```yaml
models:
  default: # name of the model, used in the inference process as a part of path, like /rank/default
    type: lambdamart # model type
    backend:
      type: xgboost # supported values: xgboost, lightgbm for lambdamart model
      iterations: 100 # optional (default 100), number of iterations while training the model
      seed: 0 # optional (default = random), a seed to make training deterministic
    weights: # types and weights of interactions used in the model training
      click: 1 # you can increase the weight of some events to hint model to optimize more for them
    features: # features from the previous section used in the model
    - popularity
    - genre
  # You can specify several models at once.
  # This can be useful for A\B test scenarios or while testing different sets of features.

  #random:
  #  type: shuffle # shuffle model type produces random results
  #  maxPositionChange: 5 # controls the amount of randomness that shuffle can introduce in the original ranking

  # The noop model does nothing with the original ranking and returns results "as is"
  #noop:
  #  type: noop
```


## API

The "api" section describes the Metarank API configuration. This section is optional and by default binds 
service to port 8080 on all network interfaces.
```yaml
api:
  port: 8080
  host: "0.0.0.0"
```

## Data sources

The optional "source" section describes the source of the data, and by default expects you to submit all
user feedback using [the API](../api.md). Check [Supported data sources](data-sources.md) for more information.

```yaml
source:
  type: file # source type, available options: file, kafka, pulsar, kinesis
  #path: /home/user/ranklens/events/ # path to events file, alternatively you can use CLI to provide file location
  #offset: earliest|latest|ts=<unixtime>|last=<duration> #default: earliest
  #format: <json|snowplow:tsv|snowplow:json> # file format, default: json

  # Check https://docs.metarank.ai/reference/overview/data-sources#apache-kafka for more information
  #type: kafka
  #brokers: [broker1, broker2]
  #topic: events
  #groupId: metarank
  #offset: earliest|latest|ts=<unixtime>|last=<duration>
  #format: <json|snowplow:tsv|snowplow:json>

  # Check https://docs.metarank.ai/reference/overview/data-sources#apache-pulsar for more information
  #type: pulsar
  #serviceUrl: <pulsar service URL>
  #adminUrl: <pulsar service HTTP admin URL>
  #topic: events
  #subscriptionName: metarank
  #subscriptionType: exclusive # options are exclusive, shared, failover
  #offset: earliest|latest|ts=<unixtime>|last=<duration>
  #format: <json|snowplow:tsv|snowplow:json>

  # Check https://docs.metarank.ai/reference/overview/data-sources#aws-kinesis-streams for more information
  #type: kinesis
  #region: us-east-1
  #topic: events
  #offset: earliest|latest|ts=<unixtime>|last=<duration>
  #format: <json|snowplow:tsv|snowplow:json>

```

## Core

This optional section contains parameters related to the metarank service itself. Default setup:
```yaml
core:
  
  # How rankings and interactions are joined into click-throughs. For details, see the section below in this doc.
  clickthrough:
    maxParallelSessions: 10000 # how many active sessions may happen within a `maxSessionLength` period

    maxSessionLength: 30m # after which period of inactivity session is considered finalized
    # default = 30m (to be consistent with Google Analytics)
    
  # Anonymous usage reporting. It is very helpful to us, so please leave this enabled.
  tracking:
    analytics: true
    errors: true

```


### Click-through joining

Metarank joins ranking and interaction events together into click-through chains, which are later used for machine learning model training.

As interactions are happening some time later than rankings, Metarank needs to keep a set of rankings in the buffer,
awaiting all the interactions that may happen later. 

This buffer policy is controlled by the following parameters:
* `core.clickthrough.maxSessionLength`: after which time period the session should be considered finalized, so no more
interactions are allowed to happen. Default values is 30m, as in Google Analytics.
* `core.clickthrough.maxParallelSessions`: how many parallel sessions may hang in buffer awaiting interactions. Default 
is 10k.

### Anonymous usage analytics

By default, Metarank collects anonymous usage analytics to improve the tool. No IP addresses are being tracked,
only simple counters track what parts of the service are being used.

It is very helpful to us, so please leave this enabled. Counters are sent to `https://analytics.metarank.ai` on each 
service startup.

* We never share collected data with anyone else.
* Data is stored for 1 year, and then removed.
* Collector code running on is open-source: [github.com/metarank/metarank-lambda-tracker](https://github.com/metarank/metarank-lambda-tracker)

An example payload:
```json5
{
  "state" : "memory",
  "modelTypes" : [ "shuffle" ],
  "usedFeatures" : [
    {
      "name" : "price",
      "type" : "number"
    }
  ],
  "system" : {
    "os" : "Linux",
    "arch" : "amd64",
    "jvm" : "17.0.3",
    // a SHA256 of your network interface MAC address, used as an installation ID
    "macHash" : "3e78137877f66cfb4f1a0875e7eadb3100fb3c6c4755089b3cc6d9f074a3c4b5"
  },
  "mode" : "train",
  "version" : "snapshot",
  "ts" : 1662030509517
}
```
### Error logging

We use [Sentry](https://www.sentry.io) for error collection. This behavior is enabled by default and can be disabled with
`core.tracking.errors: false`. Sentry is configured with the following options:
* Breadcrumbs are disabled: so it won't share parts of your console log with us.
* PII tracking is disabled: no hostnames and IP addresses are included in the error message.

An example error payload is available in [sample-error.json](sample-error.json).

The whole usage logging and error reporting can be disabled also by setting an env variable to `METARANK_TRACKING=false`.

