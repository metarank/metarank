# Metarank Documentation

For a general overview of problems solved by Metarank and how to approach them, see a intro walkthrough guide 
about [Personalizing recommendations with Metarank](https://medium.com/metarank/personalizing-recommendations-with-metarank-f2644112536b). 

## Overview

* [Intro](intro.md): what Metarank is and which problems are solved by it.
* [Technical overview](tech-overview.md): how Metarank works and how can it be integrated into your system.
* [Click models](click-models.md): how relevance judgements are mapped from clicks, and how ranking is optimized.

## Run and deploy

* [Supported platforms](supported-platforms.md): on which OS/JVM Metarank can run.
* [AWS](deploy/aws-s3.md): (Work-in-progress) deploying Metarank to AWS.
* [Docker](deploy/docker.md): running Metarank from Docker.
* [CLI Options](deploy/cli-options.md): running Metarank from command-line.
* [API Overview](api_schema.md): sending REST API requests for reranking and feedback ingestion.
* [Using different data sources](data-sources.md) like Kafka and Pulsar 

## Configure

* [Configuration](configuration.md): config file structure and possible running modes.
* [Feature extractors](feature-extractors.md): configuring the way events are mapped to ML features.
* [Feature scopes](scopes.md): ML features can be bound to a specific scope (like count number of clicks per *item* - so item is the scope) 
* [Event schema overview](event-schema.md): How input events about visitor activity look like.
* [Event sources](data-sources.md): Which sources Metarank can pull events from.
* [ML ranking models](supported-ranking-models.md): which ML models can be used for ranking.

## Integrations

* [Snowplow](integrations/snowplow.md): use [Snowplow Analytics](https://snowplowanalytics.com/) for collecting events needed by Metarank.

## Tutorials

* [Demo tutorial](tutorial_ranklens.md): a shorter version of 
[Personalizing recommendations with Metarank](https://medium.com/metarank/personalizing-recommendations-with-metarank-f2644112536b) article
to reproduce the [demo.metarank.ai](https://demo.metarank.ai). 