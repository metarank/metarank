# Technical overview


Metarank is not a replacement to search engines like ElasticSearch, Solr or Sphinx, but is a supplementary tool to 
perform a multi-phase ranking scheme.

![integragion of Metarank](img/integration.svg)

Metarank consists of three main parts:
1. **Ingestion**: takes a stream of real-time feedback events performed by your customers, computes all 
the parameters needed to run the ML model and writes them into a persistent storage.
2. **API**: on each rerank request reads the subset of the data from the storage, computes all the numerical
feature values for the ML model input, and then runs the model.
3. **Offline** training: exports the complete historical dataset from the storage in a XGBoost/LightGBM/RankNet
compatible format and performs the ML model training.

## Ingestion

This part of Metarank performs all the heavy-lifting required to update it's internal state of the
world representation used for ranking. It is done with the following steps:
 
* Accept event via REST API (or Kafka/Pulsar stream) and validate its schema.
* Update all the dependant feature values' internal states.
* Periodically flush the state to the storage, so it can be used for ranking.

Ingestion process can be either 
* **Online** with the real customer traffic.
* **Offline** that happens during re-bootstrapping the system after altering the schema or importing the historical data.

State flushing period depends on the feedback event types and tries to minimize the load on the storage backend. 
For example, there is no need to have an exact real-time number of clicks updated in the DB:
* Too many heavy writes will overload the storage.
* Once-in-hour eventually-consistent snapshot is enough for the online ranking.

There are four main types of feedback events:
1. *Item metadata* event: a descriptor of the item itself you're ranking with all the fields relevant for the ranking.
For example, you should emit this type of event when the item changes.
2. *User metadata* event: when external parameters about the current visitor change. For example, user switched the
subscription plan from free to paid.
3. *Ranking* event: a description of what items in which order were displayed to the customer. 
4. *Feedback* event: how customer interacted with the ranking. For example, made a click, added item to cart or completed
a purchase.

### Item metadata event

Item metadata is needed for Metarank to know as much as possible about the things you're ranking.

In eCommerce, for example, it can be product title, price, color, sizes and so on. From the developer perspective, this event contains the following fields:
* Unique item identifier.
* Timestamp of the update (see [timestamp format description](timestamp-formats.md) on which formats are supported)
* A set of fields describing the item.

Metarank uses a strict predefined schema for item metadata fields, that must be defined beforehand in the config file. 
See the [configuration](configuration.md) section of the docs on how to implement it. 

Here is an example JSON for the metadata event:
```json
{
  "type": "item",
  "id": "product1",
  "timestamp": "1599391467000",
  "fields": [
    {"name": "title", "value": "Nice jeans"},
    {"name": "price", "value": 25.0},
    {"name": "color", "value": ["blue", "black"]},
    {"name": "availability", "value": true}
  ]
}
```

### Ranking event 

To help train the ML model, we need to show it which items were presented to the customer. This event consists of 
the following fields:
* Unique request identifier, so we can later track all the feedback events followed by this ranking.
* Timestamp of the event (see [timestamp format description](timestamp-formats.md) on which formats are supported).
* Ranking scope.
* Ordered list of items shown to the customer.

Here is an example JSON for the ranking event:
```json
{
  "type": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "scope": {
    "user": "user1",
    "session": "session1",
    "fields": [
      {"name": "query", "value": "jeans"},
      {"name": "source", "value": "search"}
    ]
  },
  "items": [
    {"id": "product3", "relevancy":  2.0, "features": [1.0, 2.0, 3.0]},
    {"id": "product1", "relevancy":  1.0, "features": [1.0, 2.0, 3.0]},
    {"id": "product2", "relevancy":  0.5, "features": [1.0, 2.0, 3.0]} 
  ]
}
```

This event is usually emitted by the Metarank API, as it has all the information about the ranking process.

The *scope* part describes the context in which the ranking happened. It can be very specific to the business domain
you're operating in and needs to be defined in the schema configuration beforehand.

A per-product relevancy score field should be supplied by your external search system. For ElasticSearch, for example,
it can be a BM25 score between the query used for the search and product fields.

### Feedback event

Feedback event describes how the customer interacted with the ranking, so that Metarank can understand how to improve the ranking in the future. 

It contains the following fields:
* Item identifier.
* User and session identifier.
* Feedback type.
* Parent ranking request identifier.

All these identifiers are needed to link the interaction with the ranking used to produce it.

Here is an example JSON for the feedback event:
```json
{
  "id": "0f4c0036-04fb-4409-b2c6-7163a59f6b7d",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "type": "click",
  "item": "product1"
}
```
Interaction type must be predefined in the [schema file](configuration.md).

## API

This part of Metarank is used to serve the ML model to the customers. 
It pulls the current state-of-world snapshot part from the storage, computes all the required numerical feature values
needed for the ML model, feeds it with the numbers and emits the final ranking.

The API schema is **not yet completely defined**, but the general idea is to have it in the following way:
* User/session identifier.
* Timestamp (see [timestamp format description](timestamp-formats.md) on which formats are supported)
* Ranking scope.
* List of items with their external relevancy.

Here is an example JSON for the API event:
```json
{
  "id": "9747e2fb-3da4-48ee-9ae2-6d09fc455622",
  "timestamp": "1599391467000",
  "scope": {
    "user": "u1",
    "session": "s1",
    "fields": [
      {"name": "source", "value": "search"}
    ],
    "items": [
      {"id": "p1", "relevancy": 1.0},
      {"id": "p2", "relevancy": 0.1}
    ]
  }
}
```

Metarank then can use the external relevancy as one of the input feature values for the final
ranking together with all the other things precomputed during the ingestion phase.

API will also store all the feature values it computed for the ranking in the DB, so it can be
used later to periodically retrain the model using fresh data.

## Storage

Metarank is storage-agnostic and has pluggable adaptors for existing databases, so it can be
quite flexible while integrating with existing infrastructures.

Right now Metarank supports following connectors:
* Redis, as the one which is simple and fast enough for testing and prototyping.

Planned connectors are:
* Postgres, as the most widely used DB.
* Cassandra, as it can scale up linearly for heavy write traffic.

Metarank is not using any complicated and db-specific data types and can be in future 
integrated with other databases, which can provide a fast enough key-value interface. 

## Offline training

To serve a ML model, you need to first train it. Metarank is not trying to replace a widely-used
tools in data science community, but to be a nice addition to automate the most cumbersome
data processing parts.

To perform offline training, Metarank will pull all the historical information from the database,
and save it in the format compatible with XGBoost/LightGBM/RankNet. It will also generate
a template config file for the ML tool, so you can train the model without any prior experience with it.

The general idea on why training process should be offline is mostly based on an assumption that
the training process is extremely CPU heavy, especially on large datasets, and should be perfromed on a special
hardware with high RAM and CPU resources.