# Metarank Documentation

For a general overview of problems solved by Metarank and how to approach them, see a intro walkthrough guide 
about [Personalizing recommendations with Metarank](https://medium.com/metarank/personalizing-recommendations-with-metarank-f2644112536b). 

## Overview

* [Intro](01_intro.md): what Metarank is and which problems are solved by it.
* [Technical overview](02_tech_overview.md): how Metarank works and how can it be integrated into your system.

## Run and deploy

* [AWS](deploy/aws.md): (Work-in-progress) deploying it to AWS.
* [Docker](deploy/docker.md): running Metarank from Docker.
* [CLI Options](deploy/cli-options.md): running Metarank from command-line.
* [API Overview](xx_api_schema.md): sending REST API requests for reranking and feedback ingestion.

## Configure

* [Configuration](03_configuration.md): config file structure and possible running modes.
* [Feature extractors](feature_extractors.md): configuring the way events are mapped to ML features.
* [Feature scopes](scopes.md): ML features can be bound to a specific scope (like count number of clicks per *item* - so item is the scope) 
* [Event schema overview](xx_event_schema.md): How input events about visitor activity look like.
* [Event sources](data-sources.md): From which sources Metarank can pull events from.

## Tutorials

* [Demo tutorial](tutorial_ranklens.md): a shorter version of 
[Personalizing recommendations with Metarank](https://medium.com/metarank/personalizing-recommendations-with-metarank-f2644112536b) article
to reproduce the [demo.metarank.ai](https://demo.metarank.ai). 