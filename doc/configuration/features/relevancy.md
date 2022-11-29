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

Metarank [ranking event schema](../../event-schema.md) allows adding a per-item fields, which can be used for relevance score, see the example: 
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
    {"id": "item3", "fields": [{"name": "relevancy", "value": 2.0}]},
    {"id": "item1", "fields": [{"name": "relevancy", "value": 1.0}]},
    {"id": "item2", "fields": [{"name": "relevancy", "value": 0.1}]} 
  ]
}
```

This per-item "relevancy" field is the one holding information about score from the upstream ranking system, like BM25 score.

Metarank <= 0.5.10 included now deprecated `relevancy` extractor. With Metarank 0.5.11+ you can use regular [`number`](scalar.md#numerical-extractor) extractor for this case:

```yaml
- name: relevancy
  type: number
  scope: item
  field: item.relevancy
```

## Position

A bias elimination technique based on a paper [PAL: a position-bias aware learning framework for CTR prediction in live recommender systems](https://www.researchgate.net/publication/335771749_PAL_a_position-bias_aware_learning_framework_for_CTR_prediction_in_live_recommender_systems).
* on offline training, the feature value equals to the item position in the ranking
* on online inference, it is equals to a constant position value for all items.

The main idea of such approach is that the underlying ML model will learn the impact of position on ranking, but then,
setting all items position factors to the same constant value, you make it look like from the model point-of-view that
all items are located on the same position. So position has constant impact on the ranking for all the items.

To configure this feature extractor, use the following YAML snippet:
```yaml
- type: position
  name: position
  position: 5
```

To choose the best `position` value:
* Start with a value around middle of your average ranking length. So if you present 20 items, set it to 10. Usually it's
  already a good number for most of the cases.
* Do a grid search of the best value around it with `metarank standalone`. Select the best `position` based on offline NDCG value.

