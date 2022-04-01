<h1 align="center">
    <a style="text-decoration: none" href="https://www.metarank.ai">
      <img width="120" src="https://raw.githubusercontent.com/metarank/metarank/master/doc/img/logo.svg" />
      <p align="center">Metarank: real time personalization as a service</p>
    </a>
</h1>
<h2 align="center">
  <a href="https://metarank.ai">Website</a> | <a href="https://metarank.ai/slack">Community Slack</a> | <a href="https://medium.com/metarank">Blog</a> | <a href="https://demo.metarank.ai">Demo</a>
</h2>

[![CI Status](https://github.com/metarank/metarank/workflows/Scala%20CI/badge.svg)](https://github.com/metarank/metarank/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/metarank/metarank)
![Last release](https://img.shields.io/github/release/metarank/metarank)
[![Join our slack](https://img.shields.io/badge/Slack-join%20the%20community-blue?logo=slack&style=social)](https://metarank.ai/slack)


## Why Metarank?

Metarank can run on-premises or in the cloud, making it easy to get state-of-the-art ML technologies by teams having little to no ML experience. 

Building an in-house personalization solution takes around 6 months by an experienced team, and only large companies can afford it.

With Metarank, you can get up and running with personalization in *days instead of months* and be in control of **data privacy**, **optimization goals** and how Metarank is integrated into the infrastructure with **minimal vendor-lock**.

## Demo 

We have a built a [Demo](https://demo.metarank.ai) which showcases how you can use [Metarank](https://metarank.ai) in the wild. 

The [Demo](https://demo.metarank.ai) utilizes [Ranklens](https://github.com/metarank/ranklens) dataset that we have built 
using [Toloka](https://toloka.ai/) service to gather user interactions. 

Application code can be found [here](https://github.com/metarank/demo) and you can see how easy it is to query
[Metarank](https://metarank.ai) installation to get real-time personalization. 

Check out the step-by-step **[Tutorial](doc/tutorial_ranklens.md)** of running Metarank Demo locally.

## Running Metarank

* [Technical overview](doc/02_tech_overview.md) of the way it can be integrated in your existing tech stack.
* [Configuration](doc/03_configuration.md) walkthrough
* [API overview](doc/xx_api_schema.md)
* [Feature extractors](/doc/xx_feature_extractors.md)
* [CLI Options](doc/deploy/cli-options.md)
* [Running Metarank in Docker](doc/deploy/docker.md)
* [Contribution guide](CONTRIBUTING.md)
* [License](LICENSE)

### Technical prerequisites

We recommend running Metarank with [Docker](doc/deploy/docker.md).

If you would like to try it natively, here are the requirements:

* Linux or MacOS on x86. Windows support is coming soon
* [JVM 11](https://www.oracle.com/java/technologies/downloads/)


Licence
=====
This project is released under the Apache 2.0 license, as specified in the LICENSE file.
