# Personalizing recommendations

This tutorial describes how to make personalized recommendations based on a [Ranklens](https://github.com/metarank/ranklens) dataset.
The dataset itself is a repackaged version of famous Movielens dataset with recorded real human clickthroughs on movies.

This tutorial reproduces the system running on [demo.metarank.ai](https://demo.metarank.ai).

### Prerequisites

- [JDK 21+](https://adoptium.net/installation/) installed on your local machine
- a running [Redis](https://redis.io/download) instance (optional: Metarank stores state in memory by default)
- latest [release jar file](https://github.com/metarank/metarank/releases) of Metarank

For the data input, we are using a repackaged copy of the ranklens dataset [available here](https://github.com/metarank/metarank/tree/master/src/test/resources/ranklens/events). 
The only difference with the original dataset is that we have converted it to a metarank-compatible data model with 
item/user/interaction/ranking [event format](./event-schema.md).

### Configuration

An example ranklens-compatible config file is available [here](https://github.com/metarank/metarank/blob/master/src/test/resources/ranklens/config.yml),
but you can write your own based on that template:
```yaml
api:
  port: 8080
  host: "0.0.0.0"

state:
  type: redis
  host: localhost
  port: 6379

models:
  xgboost:
    type: lambdamart
    backend:
      type: xgboost
      iterations: 10
      seed: 0
    weights:
      click: 1
    features:
      - popularity
      - vote_avg
      - vote_cnt
      - budget
      - release_date
      - runtime
      - title_length
      - genre
      - ctr
      - liked_genre
      - liked_actors
      - liked_tags
      - liked_director
      - visitor_click_count
      - global_item_click_count
      - day_item_click_count
features:
  - name: popularity
    type: number
    scope: item
    source: item.popularity

  - name: vote_avg
    type: number
    scope: item
    source: item.vote_avg

  - name: vote_cnt
    type: number
    scope: item
    source: item.vote_cnt

  - name: budget
    type: number
    scope: item
    source: item.budget

  - name: release_date
    type: number
    scope: item
    source: item.release_date

  - name: runtime
    type: number
    scope: item
    source: item.runtime

  - name: title_length
    type: word_count
    source: item.title
    scope: item

  - name: genre
    type: string
    scope: item
    source: item.genres
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
    field: item.genres
    scope: session
    count: 10
    duration: 24h

  - name: liked_actors
    type: interacted_with
    interaction: click
    field: item.actors
    scope: session
    count: 10
    duration: 24h

  - name: liked_tags
    type: interacted_with
    interaction: click
    field: item.tags
    scope: session
    count: 10
    duration: 24h

  - name: liked_director
    type: interacted_with
    interaction: click
    field: item.director
    scope: session
    count: 10
    duration: 24h

  - name: visitor_click_count
    type: interaction_count
    interaction: click
    scope: session

  - name: global_item_click_count
    type: interaction_count
    interaction: click
    scope: item

  - name: day_item_click_count
    type: window_count
    interaction: click
    scope: item
    bucket: 24h
    periods: [7,30]
 ```

### 1. Importing historical data

The import job will process your historical events based on the config file, compute all the feature values and
click-through joins, and write them to the state store:

```shell
java -jar metarank.jar import --config config.yml --data src/test/resources/ranklens/events/
```

### 2. Training the Machine Learning model

When the import is finished, you can train the model using the same `config.yml`. The training job will build the
dataset from the imported click-throughs, do the actual training and store the model in the state store:

```shell
java -jar metarank.jar train --config config.yml --model xgboost
```

### 3. Inference

Run Metarank REST API service to process feedback events and rerank in real-time. 
By default Metarank will be available on `localhost:8080` and you can send feedback events to `http://<ip>:8080/feedback` 
and get personalized ranking from `http://<ip>:8080/rank/<MODEL_NAME>`. 
```shell
java -jar metarank.jar serve --config config.yml
```

You can also run all three stages at once with the `standalone` mode:
```shell
java -jar metarank.jar standalone --config config.yml --data src/test/resources/ranklens/events/
```

## Playing with it

You can check out how we use the Metarank REST API in our [Node.js demo application](https://github.com/metarank/demo). 
Each feedback event will influence the ranking results, so the more you use the service, the better ranking you will get.
