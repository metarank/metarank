# Metarank

[![CI Status](https://github.com/meta-rank/metarank/workflows/Scala%20CI/badge.svg)](https://github.com/metarank/metarank/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/metarank/metarank)
![Last release](https://img.shields.io/github/release/metarank/metarank)

[Metarank](https://www.metarank.ai/) (or METAdata RANKer) is a low-code Machine Learning personalization tool that can be used to build personalized ranking systems.
You can use [Metarank](https://www.metarank.ai/) to personalize product listings, articles, recommendations, and search results in order to your boost sales. 
It automates the most common data processing tasks in Learn-To-Rank applications.

## Why Metarank?

Building personalized ranking systems is not an easy task even for a team of experienced data scientists and it can take months to setup data pipelines, storages and model training.
[Metarank](https://www.metarank.ai/) automates the most common tasks that are required to add personalization to your product listings, articles and any other type of content.
Instead of months, it will take days or even a few hours to create and deploy a personalized model to get benefit from personalization and concentrate on improving the model.

You don't even need to have Machine Learning experts in the team to integrate [Metarank](https://www.metarank.ai/) with your application!

Here's a high level overview of [Metarank](https://www.metarank.ai/) integration:

* define your features with simple YAML configuration file
* send historical events and metadata via a JSON API
* run [Metarank](https://www.metarank.ai/) to train the model
* send real-time events to a running instance of [Metarank](https://www.metarank.ai/)
* use pre-trained model to personalize your listings in real-time

### High-level Metarank feature overview

* Built-in feature store to compute features used for online and offline training
* YAML configuration to define the structure of your data and features that can include:
    * simple scalar features (e.g. number of clicks)
    * scoped features (e.g. item CTR for a specific query)
    * relative features (e.g. percentage of clicks per item over the total number of clicks)
    * user-specific features (e.g. user agent parser, geoip)
* REST API or Kafka connector to receive events and metadata updates
* Offline and online (real-time personalization) operation modes
* Explain mode to understand how final ranking is computed
* Local mode to run [Metarank](https://www.metarank.ai/) locally without deploying to a cluster
* Cloud native: deploy [Metarank](https://www.metarank.ai/) to Kubernetes or AWS

## In-depth Docs

* [Introduction](doc/01_intro.md) to the Metarank
* [Technical overview](doc/02_tech_overview.md) of the way it can be integrated in your existing tech stack.
* [Configuration](doc/03_configuration.md) walkthrough
* [Contribution guide](doc/xx_development.md)
* [License](LICENSE)

Current state
=====
Metarank is an Alpha: it's early days of development. It is well-covered with tests and runs in production several systems serving real traffic, although we don't recommend yet to run it without developer support.

Licence
=====
This project is released under the Apache 2.0 license, as specified in the LICENSE file.
This application is neither endorsed by, nor affiliated with, Findify AB.
