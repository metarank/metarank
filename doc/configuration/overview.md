# Configuration

Metarank YAML config file contains the following sections:
* [Persistence](persistence.md)
* [Model Configuration](supported-ranking-models.md)
* [Feature extractors](feature-extractors.md): how features are computed on top of incoming events
* [API options](#api-options)
* [Data sources](data-sources.md)

```yaml
state:
  type: memory

features:
  - name: popularity
    type: number
    scope: item
    source: item.popularity

  - name: genre
    type: string
    scope: item
    source: item.genres

models:
  default:
    type: lambdamart
    backend:
      type: xgboost
      iterations: 10
      seed: 0
    weights:
      click: 1
    features:
      - popularity
      - genre

source:
  type: file

api:
  port: 8080
  host: "0.0.0.0"
```

See the [sample-config.yml](sample-config.yml) file for more detailed config format description.

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
```

Metarank will expect the `popularity` field to be a number and the `genres` to be a string or a list of strings and
the metadata event will have the following structure

```json5
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
Check out [Event Format](../event-schema.md) for more information.

## API options

You can specify a custom port and host where Metarank's API will be running. 

By default Metarank uses the following configuration:

```yaml
api:
  port: 8080
  host: "0.0.0.0"
```
