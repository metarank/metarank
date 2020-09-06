# Introduction

Right now, when a company is interested in ranking items (in ecommerce category/collection pages, 
search results, autocomplete suggestions or recommendations), they have to solve the same problems 
again and again:
* collecting feedback clickthrough data like clicks and item lists
* storing them in a way both suitable for ML model training and online inference
* backtesting the whole clickthrough history to perform offline model evaluation
* online/offline feature recomputation
* online low-latency model inference

All of these steps are not new and there is a ton of literature and common knowledge how to solve 
them separately. But glueing a single integrated solution from these parts can be not an easy 
task, usually requiring hiring a team of developers, data scientists and infra engineers to build 
this system from scratch.

There are usually a ton of shared building blocks between these systems built internally by 
different companies, but when a new company also wants to build a similar system, it has to go 
over the same problems again and again. 

## Metarank

Metarank (METAdata RANKer) is a LTR all-in-one application, which tries to solve common pain 
points while building typical ranking systems:
* a simple API to perform secondary item reranking. Primary ranking should be done by existing 
IR systems like ElasticSearch, Solr, Spark, etc. 
* item-agnostic ML model serving: can be used to rank items in search results, 
category collections, recommendations and autocomplete suggestions.
* Feature store suitable both for backtesting and online model serving, 
with plugins for different DBs
* Data collection API, which can backfill historical data and receive a stream of online clickthroughs.
* A set of basic feature value extractors, so you can build a trivial LTR model without any coding. 
Feature extractor interface should also be extensible so you can plug your own.
* Support different ranking algorithms and libraries. Planned ones are XGBoost, LightGBM, Catboost 
and RankLib. 

## Similar solutions

We’re not the first ones thinking about this problem, as there are a couple of similar solutions 
in the market:
* ElasticSearch-LTR plugin. This plugin is much narrower in supported scope, as feature management, backtesting and training phases should be implemented separately, which is really complicated. ES-LTR only serves XGBoost models inside ES and supports some basic product-based feature extraction.
* prediction.io can also solve the same problem, but:
    * abandoned after SalesForce acquisition
    * requires HDFS and a ton of pre-installed infrastructure to operate
    * Not focused on ranking, so still requires a lot of manual work to operate.

Metarank feature store part is also looking similar to some existing generic feature stores 
like Feast or Hopsworks, but less generic.

## Why Metarank

In theory, if a company has a strong team of DS/SW/DevOps engineers, it can glue a similar 
system together in quite a short period of time, like 3-6 months. The problem is that 
frequently the existing team has not enough experience with ranking problems, and the first 
version of the system can be a complete failure (a quote from airbnb LTR article), 
requiring a redesign and major refactoring.

The problems facing teams building a yet another LTR system are usually the same:
* Feature storage for both online and offline evaluations are tricky to design to implement. 
Existing ones are operationally complex as they are too generic.
* Backtesting new models requires a lot of hand-written code to glue existing libraries together.
* Existing ML systems are not really focused on ranking problems and have weak integrations 
with existing LTR libraries.

Metarank should solve these problems by being a very narrowly scoped and a bit opinionated system, mostly focused on non-LTR-experts:
* Easy to test and deploy.
* Documented and plain API for raw clickthrough ingestion, with strict event schema
* DB-agnostic feature store
* API for online ranking

## Who can be interested in metarank?

Metarank is mostly focused on medium and large companies having their own discovery teams, 
working on ranking and recommendations. Metarank should help discovery teams to simplify
 their LTR stack for data collection, backtesting and model serving. As an open source project, 
 it can also become an umbrella project to combine contributions from different companies in the 
 area of ranking.
