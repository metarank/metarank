# Introduction

Currently, when a company is interested in ranking items (in ecommerce category/collection pages, 
search results, autocomplete suggestions or recommendations), it has to solve the same problems 
again and again:
* Collecting feedback data (clickthrough data like clicks in item lists, add to carts or other actions performed by a user).
* Storing this data in a way both suitable for ML model training and online inference.
* Backtesting the whole clickthrough history to perform offline model evaluation.
* Online/offline feature recomputation.
* Online low-latency model inference.

All of these steps are not new and there is a lot of literature and common knowledge about solving
these tasks separately. However, glueing a single integrated solution from these parts is not an easy 
task, usually requiring hiring a team of developers, data scientists and infrastructure engineers to build 
this system from scratch.

Companies that have such systems already have the building blocks, but they are internal and not available for general public, 
so when a new company wants to build the same system it has to go over the same problems again and again.

## Metarank

Metarank (METAdata RANKer) is a LTR (learn-to-rank) all-in-one application, which solves common pain 
points while building typical ranking systems:
* Simple API to perform **secondary** item reranking. *Primary* ranking should be done by existing 
IR systems like ElasticSearch, Solr, Sphinx, Spark, etc. 
* Item-agnostic ML model serving that can be used to rank items in search results, 
category (or item collections), recommendations and autocomplete suggestions.
* Feature store that is suitable both for backtesting and online model serving, 
with plugins for different DBs.
* Data collection API, which can backfill historical data and receive a stream of online clickthroughs.
* A set of basic feature value extractors, so you can build a trivial LTR model without any coding. 
Feature extractor interface should also be extensible so you can plug your own implementation.
* Support different ranking algorithms and libraries. Planned ones are XGBoost, LightGBM, Catboost 
and RankLib. 

## Similar solutions

We’re not the first ones thinking about how to solve this problem, there are several similar solutions 
in the market:
* ElasticSearch-LTR plugin. This plugin has a much narrower scope: *feature management*, *backtesting* and *training* phases should be implemented separately, which is really complicated. ES-LTR only serves XGBoost models inside ElasticSearch and supports some basic product-based feature extraction.
* prediction.io can also solve the same problem, but:
    * Abandoned after SalesForce acquisition.
    * Requires HDFS and a ton of pre-installed infrastructure to operate.
    * Not focused on ranking, so still requires a lot of manual work to operate.

Metarank feature store also looks similar to some existing generic feature stores 
like Feast or Hopsworks, but is less generic.

## Why Metarank

In theory, if a company has a strong team of data scientists, software and devops engineers, it can glue a similar 
system together in quite a short period of time (around 3-6 months). However, usually such existing teams do not enough experience with ranking problems, and the first 
version of the system can be a complete failure (a quote from airbnb LTR article), requiring a redesign and major refactoring.

The teams that build yet another LTR system usually face the same problems:
* Feature storage for both online and offline evaluations is tricky to design and implement. 
Existing ones are operationally complex as they are too generic.
* Backtesting new models requires a lot of hand-written code to glue existing libraries together.
* Existing ML systems are not really focused on ranking problems and have weak integrations with existing LTR libraries.

Metarank should solve these problems by being a very narrowly scoped and a bit opinionated system, mostly focused on non-LTR-experts:
* Easy to test and deploy.
* Documented and plain API for raw clickthrough ingestion with strict event schema.
* DB-agnostic feature store.
* API for online ranking.

## Who can be interested in metarank?

Metarank is mostly focused on medium and large companies having their own discovery teams that work on ranking and recommendations. 
Metarank should help discovery teams to simplify their LTR stack for data collection, backtesting and model serving. 
As an open source project, it can also become an umbrella project to combine contributions from different companies in the area of ranking.
