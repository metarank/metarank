# Relevvancy feature extractors

## Ranking

While implementing Learn-to-Rank systems, Metarank is designed to be a satellite secondary reranking system. It 
assumes that there exists another service which generates candidates for the reranking process:
* in search: ElasticSearch or SOLR
* in recommendations: output of spark mmlib ALS recommender
* in ecommerce: inventory database

Most of these primary sources of input may also have a per-item score: how much this item is matching the original query:
* BM25 or TF/IDF score in search
* cosine difference between embeddings in recommendations

Metarank [ranking event schema](../event-schema.md) has a special field for it, see the example: 
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "query", "value": "cat"},
      {"name": "source", "value": "search"}
  ],
  "items": [
    {"id": "item3", "relevancy":  2.0},
    {"id": "item1", "relevancy":  1.0},
    {"id": "item2", "relevancy":  0.5} 
  ]
}
```

This per-item "relevancy" field is the one holding information about score from the upstream ranking system, like BM25 score.
To use this information, you should configure the corresponding extractor:
```yaml
- name: relevancy
  type: relevancy
```

There are no options to configure, as relevancy is taken only from ranking events and never stored separately.

## Items count

Counts the number of items from the ranking event:

```yaml
- name: displayed_items
  type: items_count
```