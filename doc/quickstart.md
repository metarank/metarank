# Quickstart

This guide shows how to install and run Metarank on a single machine using Docker. We're going to run the service, feed it with
sample data and issue queries.

## Prerequisites

* Docker: [Docker Desktop for Mac/Windows](https://docs.docker.com/engine/install/), or Docker for Linux
* Operating system: Linux, macOS or Windows 10+
* Architecture: x86_64. For M1, see [Apple M1 support](installation.md#apple-m1-support)
* Memory: 2Gb dedicated to Docker

This guide is tested with docker for linux v20.10.16, and [metarank/metarank:0.5.0](https://hub.docker.com/r/metarank/metarank/tags) docker image.

## Getting the dataset

For the quickstart, we're going to use an open [RankLens](https://github.com/metarank/ranklens) dataset and personalize
a set of pre-computed movie recommendations based on a visitor activity. 
The dataset is used to build a [Metarank Demo](https://demo.metarank.ai/) website. 

For this quickstart, you need two files from the dataset:
1. [config.yml](https://raw.githubusercontent.com/metarank/metarank/master/src/test/resources/ranklens/config.yml) - metarank
configuration file, describing how to map visitor events to ML features
2. [events.jsonl.gz](https://github.com/metarank/metarank/blob/master/src/test/resources/ranklens/events/events.jsonl.gz) - 
a dump of historical visitor interactions used for ML training.

```bash
[demo] $ wget https://raw.githubusercontent.com/metarank/metarank/master/src/test/resources/ranklens/config.yml
[demo] $ wget https://github.com/metarank/metarank/raw/master/src/test/resources/ranklens/events/events.jsonl.gz
[demo] $ ls -l

total 172
drwxr-xr-x  2 user user   4096 Aug 23 14:24 .
drwxr-xr-x 81 user user  16384 Aug 23 14:24 ..
-rw-r--r--  1 user user   2542 Aug 23 14:24 config.yml
-rw-r--r--  1 user user 150264 Aug 23 14:24 events.jsonl.gz

```

## Running Metarank in Docker

```bash
[demo] docker run -i -t -p 8080:8080 -v $(pwd):/opt/metarank metarank/metarank:0.5.0-SNAPSHOT-M1 standalone\
    --config /opt/metarank/config.yml\
    --data /opt/metarank/events.jsonl.gz
```

This command will:
* run the dataset import process from the current directory,
* train the [ML model for ranking](supported-ranking-models.md),
* start the [API](api_schema.md) on port 8080.

[![asciicast](https://asciinema.org/a/6D0iNkIoLWvf4vz0kvYzdqdzL.svg)](https://asciinema.org/a/6D0iNkIoLWvf4vz0kvYzdqdzL)

## First query

We're going to send a set of initial candidates for reranking into the Metarank's REST API `/rank` endpoint. Let's take
all the movies in the Fantasy genre, sorted by vote count:

```bash

```

## Sending visitor feedback

Metarank expects to receive events describing item metadata and visitor interactions.

## Personalize