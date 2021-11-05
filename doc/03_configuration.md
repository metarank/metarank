# Configuration

Metarank YAML config file contains three main sections:
* Service configuration
* Event schema definition: which field and their types are expected to be included in incoming events
* Feature extractors: how features are computed on top of incoming events
* event source: where to read input events from
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
  file:
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
8. Referer

So YAML snipped defining a field is defined in the following way:
```yaml
- name: <name of field>
  type: <one of field types>
  required: <boolean> // this field is optional, all fields are not required by default
```

So having the item metadata event example from [event schema doc](xx_event_schema.md):
```json
{
  "id": "product1", 
  "timestamp": "1599391467000", 
  "fields": [
    {"name": "title", "value": "Nice jeans"},
    {"name": "price", "value": 25.0},
    {"name": "color", "value": ["blue", "black"]},
    {"name": "availability", "value": true}
  ]
}
```

We need the following config to make Metarank accept it:

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

In the next section the way to map fields into ML features should be defined. 

Metarank supports a wide set of feature extractors with some shared properties:
* each feature extractor can be scoped by user, session and item
* the computed feature can be updated either in real-time, or with periodically

Feature extractors have a couple of shared fields, and in general, configured in the following way:
```yaml
- name: price // name of the feature
  type: scalar_number
  refresh: 1h // how frequently this feature should be updated
  source:
    <where to take source data>
```

You can follow the [feature extractors](xx_feature_extractors.md) section of docs for more details on configuring 
extractors.

