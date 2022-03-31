# Configuration

Metarank YAML config file contains the following sections:
* Service configuration
* Event schema definition: which fields and their types are expected to be included in incoming events
* Feature extractors: how features are computed on top of incoming events
* Event source: where to read input events from
* Store: where to persist computed feature values 


```yaml
api:
  port: 8080
schema:
  metadata:
    - name: price
      type: number
      required: true
  impression:
    - name: query
      type: string
  interaction:
    - name: type 
      type: string
feature:
  - name: price
    type: number
    source: price
ingest:
  file: /home/user/events.jsonl
store:
  type: redis
  host: localhost
  port: 6379
```

## Service configuration

TODO

## Ingest configuration

Supported input formats:
* file: newline-separated jsonl-encoded events from a directory
* api: RESTful endpoint so you can post your events there
* kafka: coming soon

File config example:
```yaml
ingest:
  type: file
  path: file:///home/work/input
```

API Config example:
```yaml
ingest:
  type: api
  port: 8081
```


## Event schema definition

In this section field types and names should be defined for metadata, impression and interaction events. Metarank supports
the following types of fields:
1. string: a regular UTF-8 string
2. number: a double-precision floating-point format
3. boolean: true or false
4. list\<string\>: a sequence of strings
5. list\<number\>: a sequence of numbers
6. IP Address
7. User-Agent
8. Referrer

So YAML snippet for a field is defined in the following way:
```yaml
- name: <name of field>
  type: <one of field types>
  required: <boolean> // this field is optional, all fields are optional by default
```

So having the item metadata event example from [event schema doc](xx_event_schema.md):
```json
{
  "event": "metadata",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000", // required
  "item": "item1", // required
  "fields": [
    {"name": "title", "value": "Nice jeans"},
    {"name": "price", "value": 25.0},
    {"name": "color", "value": ["blue", "black"]},
    {"name": "availability", "value": true}
  ]
}
```

We need the following config for Metarank to accept it:

```yaml
schema:
  metadata:
    - name: title
      type: string
      required: true

    - name: price
      type: number

    - name: color
      type: list<string>

    - name: availability
      type: boolean      
```

## Feature extractor configuration

Feature extractor configuration defines the way fields are mapped to features.

Metarank supports a wide set of feature extractors with some shared properties:
* each feature extractor can be scoped by user, session and item
* the computed feature can be updated either in real-time, or with a certain period

Feature extractors have a couple of shared fields, and in general, are configured in the following way:
```yaml
- name: price // name of the feature
  type: scalar_number // feature type
  refresh: 1h // how frequently this feature should be updated
  source:
    <where to take source data>
```

You can follow the [feature extractors](xx_feature_extractors.md) section of docs for more details on configuring 
extractors.

