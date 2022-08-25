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
top-100 popular movies tagged as Sci-Fi, and ask Metarank to reorder them to maximize CTR:

```json
curl -X POST http://localhost:8080/rank/xgboost -d '{
    "event": "ranking",
    "id": "id1",
    "items": [
        {"id":"72998"}, {"id":"67197"}, {"id":"77561"}, {"id":"68358"}, {"id":"79132"}, {"id":"103228"}, 
        {"id":"72378"}, {"id":"85131"}, {"id":"94864"}, {"id":"68791"}, {"id":"93363"}, {"id":"112623"}, 
        {"id":"109487"}, {"id":"59315"}, {"id":"120466"}, {"id":"90405"}, {"id":"122918"}, {"id":"70286"}, 
        {"id":"117529"}, {"id":"130490"}, {"id":"92420"}, {"id":"122882"}, {"id":"87306"}, {"id":"82461"}, 
        {"id":"113345"}, {"id":"2571"}, {"id":"122900"}, {"id":"88744"}, {"id":"111360"}, {"id":"134130"}, 
        {"id":"95875"}, {"id":"60069"}, {"id":"2021"}, {"id":"135567"}, {"id":"103253"}, {"id":"111759"},
        {"id":"122902"}, {"id":"104243"}, {"id":"112852"}, {"id":"102880"}, {"id":"56174"}, {"id":"107406"}, 
        {"id":"96610"}, {"id":"741"}, {"id":"166528"}, {"id":"164179"}, {"id":"187595"}, {"id":"589"}, 
        {"id":"71057"}, {"id":"3527"}, {"id":"6365"}, {"id":"6934"}, {"id":"1270"}, {"id":"6502"}, 
        {"id":"114935"}, {"id":"8810"}, {"id":"173291"}, {"id":"1580"}, {"id":"182715"}, {"id":"166635"}, 
        {"id":"1917"}, {"id":"135569"}, {"id":"106920"}, {"id":"1240"}, {"id":"5502"}, {"id":"316"},
        {"id":"85056"}, {"id":"780"}, {"id":"1527"}, {"id":"5459"}, {"id":"94018"}, {"id":"33493"}, 
        {"id":"8644"}, {"id":"60684"}, {"id":"7254"}, {"id":"44191"}, {"id":"101864"}, {"id":"132046"}, 
        {"id":"97752"}, {"id":"2628"}, {"id":"541"}, {"id":"106002"}, {"id":"1200"}, {"id":"5378"}, 
        {"id":"2012"}, {"id":"79357"}, {"id":"6283"}, {"id":"113741"}, {"id":"90345"}, {"id":"2011"}, 
        {"id":"27660"}, {"id":"34048"}, {"id":"1882"}, {"id":"1748"}, {"id":"2985"}, {"id":"104841"}, 
        {"id":"34319"}, {"id":"1097"}, {"id":"115713"}, {"id":"2916"}
    ],
    "user": "alice",
    "session": "alice1",
    "timestamp": 1661345221008
}'
```

## Sending visitor feedback

Metarank expects to receive events describing item metadata and visitor interactions.

## Personalize