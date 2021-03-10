# Metarank

[![CI Status](https://github.com/meta-rank/metarank/workflows/Scala%20CI/badge.svg)](https://github.com/meta-rank/metarank/actions)

Metarank is a toolbox for building personalized ranking systems. It can be used to re-rank 
items in search results, recommendations and autocomplete suggestions by automating common
data processing tasks in Learn-To-Rank applications.

## Purpose

* Collect a stream of item, search and interaction events using REST API 
or Kafka connector.
* Compute a wide variety of default feature values:
    * Multiple time windows: so each feature can be updated over 1-2-4-8-n day periods.
    * Scoping: for example, item CTR for a specific query.
    * Absolute and relative values: can track percentage of clicks per item over the
    total number of clicks.
* Store all ranking-related state using pluggable database connectors: PostgreSQL, RocksDB, Cassandra.
* Export XGBoost/LightGBM/RankNet-compatible libsvm/csv training data files.
* Serve pre-trained LTR ML models.

## Docs

* [Introduction](doc/01_intro.md) to the Metarank
* [Technical overview](doc/02_tech_overview.md) of the way it can be integrated in your existing tech stack.
* [Configuration](doc/03_configuration.md) walkthrough
* [Contribution guide](doc/xx_development.md)
* [License](LICENSE)

Current state
=====
Metarank is currently in the phase of active development and not supposed for production usage yet.
Check the issue tracker and milestones for progress.

Roadmap
=====
* **v0.1-M1**: 
    * Ingestion event schema and API: json-only.
    * Static schema definition in config file.
    * Storage interface: RocksDB implementation.
    * Feature interface: scoping and windowing support.
* **v0.1-M2**:
    * Batch jsonl ingestion API.
    * A way to store feature intermediate data in DB.
    * Relative feature support.
* **v0.1-M3**: todo

Licence
=====
This project is released under the Apache 2.0 license, as specified in the LICENSE file.
This application is neither endorsed by, nor affiliated with, Findify AB.
