# Neural LLM features

## Bi-encoder semantic similarity

A `biencoder` feature extractor computes semantic similarity between fields in item and ranking events. For example, you have an inventory with items having a `title` field:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "item1",
  "timestamp": "1599391467000",
  "fields": [ {"name": "title", "value": "red socks"} ]
}
```

And we're doing search reranking, so each ranking event has a field `query`:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "query", "value": "santa socks"}
  ],
  "items": [
    {"id": "item3"},
    {"id": "item1"},
    {"id": "item2"} 
  ]
}
```

Then with the following config snippet we can compute a cosine distance between title and query embeddings:

```yaml
- type: biencoder
  name: title_query_match
  rankingField: query
  itemField: title
  distance: cos # optional, default cos, options: cos/dot 
  encoder:
    type: bert # the only one supported for now
    model: sentence-transformer/all-MiniLM-L6-v2 # the only one supported now
```

