# Personalizing recommendations

This tutorial describes how to make personalized recommendations based on a [Ranklens](https://github.com/metarank/ranklens) dataset.
The dataset itself is a repackaged version of famous Movielens dataset with recorded real human clitkthroughts on movies.

This tutorial reproduces the system running on [demo.metarank.ai](https://demo.metarank.ai).

### Prerequisites

You need to have a JVM 11+ installed on your machine, and the Metarank 
[release jar file](https://github.com/metarank/metarank/releases) downloaded.

For the data used, we have a repackaged copy of the ranklens dataset in the github repo 
[available here](https://github.com/metarank/metarank/tree/master/src/test/resources/ranklens/events). The only difference
with the original dataset is that we converted it into a metarank-compatible data model with 
metadata/interaction/impression [event format](./xx_event_schema.md).

### Configuration

An example ranklens-compatible config file is available [here](https://github.com/metarank/metarank/blob/master/src/test/resources/ranklens/config.yml),
but you can write your own based on that template:
```yaml
interactions:
  - name: click
    weight: 1.0
features:
  - name: popularity
    type: number
    scope: item
    source: metadata.popularity

  - name: vote_avg
    type: number
    scope: item
    source: metadata.vote_avg

  - name: vote_cnt
    type: number
    scope: item
    source: metadata.vote_cnt

  - name: budget
    type: number
    scope: item
    source: metadata.budget

  - name: release_date
    type: number
    scope: item
    source: metadata.release_date

  - name: runtime
    type: number
    scope: item
    source: metadata.runtime

  - name: title_length
    type: word_count
    source: metadata.title
    scope: item

  - name: genre
    type: string
    scope: item
    source: metadata.genres
    values:
      - drama
      - comedy
      - thriller
      - action
      - adventure
      - romance
      - crime
      - science fiction
      - fantasy
      - family
      - horror
      - mystery
      - animation
      - history
      - music

  - name: ctr
    type: rate
    top: click
    bottom: impression
    scope: item
    bucket: 24h
    periods: [7,30]

  - name: liked_genre
    type: interacted_with
    interaction: click
    field: metadata.genres
    scope: session
    count: 10
    duration: 24h

  - name: liked_actors
    type: interacted_with
    interaction: click
    field: metadata.actors
    scope: session
    count: 10
    duration: 24h

  - name: liked_tags
    type: interacted_with
    interaction: click
    field: metadata.tags
    scope: session
    count: 10
    duration: 24h

  - name: liked_director
    type: interacted_with
    interaction: click
    field: metadata.director
    scope: session
    count: 10
    duration: 24h
```

### Bootstrapping

Given `events.json.gz` and `config.yml` are available, then you can run the bootstrap job:
```shell
java -cp metarank.jar ai.metarank.mode.bootstrap.Bootstrap \
  --events <dir with events.json.gz> \
  --out <output directory> \
  --config <path to config.yml>
```

The bootstrap job will process your incoming events based on a config file and produce a couple of output parts:
1. `dataset` - backend-agnostic numerical feature values for all the clickhroughs in the dataset.
2. `features` - snapshot of latest feature values, which should be used in the inference phase later.
3. `savepoint` - an Apache Flink savepoint to continue seamlessly processing online events after the bootstrap.

### Training model

When you completed the bootstrapping, you can try training the model:
```shell
java -cp metarank.jar ai.metarank.mode.train.Train \
  --input <output directory>/dataset \
  --config <path to config.yml> \
  --model-type lambdamart-lightgbm \
  --model-file <output model file> 
```

It will parse the internal representation of the training data and do the actual training. You will get a model file at the end.

### Upload

As we're using Redis for the inference, you need to load the current versions of feature values there after the bootstrap.
To do it, you need to run:
```shell
java -cp metarank.jar ai.metarank.mode.upload.Upload \
  --features-dir <output directory>/features \
  --host localhost \
  --format json
```

Which will upload the latest version of feature values to the Redis server on localhost in json format.

### Inference

And the last step, to start accepting visitor feedback via REST api on `http://<ip>:8080/feedback` and reranking
requests on `http://<ip>:8080/rank`, do the following:
```shell
java -cp metarank.jar  ai.metarank.mode.inference.Inference \
  --config <path to config.yml>\
  --model <model file>\
  --redis-host localhost\
  --format json\
  --savepoint-dir <output directory>/savepoint
```

### Playing with it

So you can send your feedback events to the `/feedback` endpoint, and then send reranking requests to the `/rank` one.
After each feedback events the ranking should change a little.