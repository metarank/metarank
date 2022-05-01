# Configuration

Metarank YAML config file contains the following sections:
* Feature extractors: how features are computed on top of incoming events
* Model configuration
* Bootstrapping options
* Inference options

```yaml
features:
  - name: popularity
    type: number
    scope: item
    source: item.popularity

  - name: genre
    type: string
    scope: item
    source: item.genres
    values:
      - drama
      - comedy
      - thriller

models:
  default:
    type: lambdamart
    path: /tmp/xgboost.model
    backend:
      type: xgboost
      iterations: 10
      seed: 0
    weights:
      click: 1
    features:
      - popularity
      - genre

bootstrap:
  eventPath: file:///ranklens/events/
  workdir: file:///tmp/bootstrap

inference:
  port: 8080
  host: "0.0.0.0"
  state:
    type: redis
    host: localhost
    format: json
```

See the [sample-config.yml](sample-config.yml) file for more detailed config format description.

## Interaction weights

Interaction define the way your users interact with the items you want to personalize, e.g. `click`, `add-to-wishlist`, `purchase`, `like`.

Interactions can be used in the feature extractors, for example to calculate the click-through rate and 
by defining `weight` you can control the optimization goal of your model: do you want to increase the amount of likes or purchases or balance between them.


You can define interaction by `name` and set `weight` for how much this interaction affects the model: 

```yaml
  - name: click // string
    weight: 1.0 // floating number
```

The `name` of the interaction must be **unique**.
The `name` is also used in the interaction events that are sent to Metarank and in the feature extractors.

## Event schema

You don't need to explicitely define the schema of the events that you will use. 

Metarank will deduce the types of the incoming fields based on your feature extactors configuration.

So, given the following feature extractor configuration: 

```yaml
  - name: popularity
    type: number
    scope: item
    source: item.popularity

  - name: genre
    type: string
    scope: item
    source: item.genres
    values:
      - drama
      - comedy
      - thriller
```

Metarank will expect the `popularity` field to be a number and the `genres` to be a string or a list of strings and
the metadata event will have the following structure

```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000", // required
  "item": "item1", // required
  "fields": [
    {"name": "popularity", "value": 25.0},
    {"name": "genres", "value": ["blue", "black"]},
  ]
}
```

Read more about [sending events in this doc](event_schema.md).

## Feature extractor configuration

Feature extractor configuration defines the way fields are mapped to features.

You can follow the [feature extractors](feature_extractors.md) section of docs for more details on configuring 
extractors.
