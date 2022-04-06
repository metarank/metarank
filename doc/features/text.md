# Text based extractors

## field_match

An extractor which can match a field from ranking event over an item field. In practice, it can be useful in search
related tasks, when you need to match a search query over multiple separate fields in document, like title-tags-category.

Given this metadata event:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "item1", 
  "timestamp": "1599391467000", 
  "fields": [
    {"name": "title", "value": "red socks"},
    {"name": "category", "value": "socks"},
    {"name": "brand", "value": "buffalo"},
    {"name": "description", "value": "lorem ipsum dolores sit amet"}
  ]
}
```

And a following ranking event:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "query", "value": "sock"}
  ],
  "items": [
    {"id": "item3", "relevancy":  2.0},
    {"id": "item1", "relevancy":  1.0},
    {"id": "item2", "relevancy":  0.5} 
  ]
}
```

With the following config file snippet you can do a per-field matching of `ranking.query` field over `item.title` field of
the items in the ranking:
```yaml
- name: title_match
  type: field_match
  itemField: item.title // must be a string
  rankingField: ranking.query // must be a string
  method:
    type: ngram // for now only ngram is supported
    n: 3
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

Supported `type` field values:

- `ngram`: does a text match using ngram algorithm

