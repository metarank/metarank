# Technical overview


Metarank is not a replacement to a search engines like ElasticSearch/Solr, but a supplementary tool to 
perform a multi-phase ranking scheme.

![integragion of Metarank](img/integration.svg)

It consists of multiple three main parts:
1. Ingestion: takes a stream of real-time feedback events from your customers, computes all 
the parameters needed to run the ML model and writes them into a persistent storage.
2. API: on each rerank request reads the subset of the data from the storage, computes all the numerical
feature values for the ML model input, and then runs the model.
3. Offline training: exports the complete historical dataset from the storage in a XGBoost/LightGBM/RankNet
compatible format and performs the ML model training.

## Ingestion

This part of Metarank is needed to perform all the heavy-lifting required to update it's internal state of the
world representation used for ranking. It is done with the following steps:
 
* accept event via REST API and validate its schema.
* update all the dependant feature value internal states.
* periodically flush the state to the storage, so it can be used for ranking.

Ingestion process can be either online with the real customer traffic or offline, when you're
re-bootstrapping the system after altering the schema (or importing the historical data).

State flushing period depends on the feedback event types and tries to minimize the load
on the storage backend. For example, there is no need to have an exact real-time number of clicks
updated in the DB:
* too many heavy writes will overload the storage.
* once-in-hour eventually-consistent snapshot can be enough for the online ranking.

There are three main types of feedback events:
1. Item metadata event: a descriptor of the item itself you're ranking will all the fields relevant for the ranking.
For example, you should emit this type of event when the item changes.
2. Ranking event: a description of what items in which order were displayed to the customer. 
3. Feedback event: how customer interacted with the ranking. For example, made a click, added item to cart or completed
a purchase.

### Item metadata event

Item metadata is needed for the Metarank to know as much as possible about the things you're ranking.

In eCommerce, for example, it can be product title, it's price, color, sizes and so on. From the developer perspective,
this event contains a couple of fields:
* unique item identifier
* update timestamp
* a set of fields describing the item 

Metarank used a strict predefined schema for item metadata fields, which need to be defined beforehand in the 
config file. See the [configuration](03_configuration.md) section of the docs on how to make it. An example JSON for 
the metadata event is looking like this:
```json
{
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
* a unique request identifier, so we can later track all the feedback events followed by this ranking,
* timestamp of the event,
* a ranking scope,
* an ordered list of items shown to the customer.

An example JSON for this event can look like this:
```json
{
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

A scope event part describes the context in which the ranking happened. It can be very specific to the business domain
you're operating in and needs to be defined in the schema configuration beforehand.

A per-product relevancy score field should be supplied by your external search system. For ElasticSearch, for example,
it can be a BM25 score between the query used for a search and product fields.

### Feedback event

A feedback event is describing how customer interacted with the ranking, so the Metarank can understand how to 
improve the ranking in the future. It contains the following fields:
* item identifier
* user and session identifier
* feedback type
* parent ranking request identifier

All these identifiers are needed to link the interaction with the ranking used to produce it.
An example interaction event looks like this:
```json
{
  "id": "0f4c0036-04fb-4409-b2c6-7163a59f6b7d",
  "timestamp": "1599391467000",
  "type": "click",
  "item": "product1"
}
```
Interaction type needs to be predefined in the [schema file](03_configuration.md).

## API

This part of Metarank is used to serve the ML model to the customers. It pulls the current
state-of-world snapshot part from the storage, computes all the required numerical feature values
needed for the ML model, feeds it with the numbers and emits the final ranking.

The API schema is not yet completely defined, but the general idea is to have it in the following way:
* user/session identifier
* timestamp
* ranking scope
* list of items with their external relevancy

Metarank then can use the external relevancy as one of the input feature values for the final
ranking among with all other things in precomputed on the ingestion phase.

API will also store all the feature values it computed for the ranking in the DB, so it can be
used later to periodically retrain the model using fresh data.

## Storage

Metarank is storage-agnostic and has pluggable adaptors for existing databases, so it can be
quite flexible while integrating with existing infrastructures.

Planned connectors are:
* Redis, as the one which is simple and fast enough for testing and prototyping.
* Postgres, as the most widely used DB.
* Cassandra, as it can scale up linearly when there is heavy write traffic.

Metarank is not using any complicated and db-specific data types and can be in future 
integrated with other databases, which can provide a fast enough key-value interface. 

## Offline training

To serve a ML model, you need to train it first. Metarank is not trying to replace a widely-used
tools in data science community, but to be a nice addition to automate the most cumbersome
data processing parts.

To perform offline training, Metarank will pull all the historical information from the database,
and save it in the format compatible with XGBoost/LightGBM/RankNet. It will also generate
a template config file for the ML tool, so you can train the model without any prior experience with it.

The general idea on why training process should be offline is mostly based on an assumption that
the training process is extremely CPU heavy especially on large datasets and should be run on a special
hardware with a ton of RAM and CPU resources.