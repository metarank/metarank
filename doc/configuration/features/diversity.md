# Ranking diversity

## diversity

Computes how different your current ranking item compared to other items within the same ranking. Numeric and string fields are supported.

### Diversification over numeric fields

Consider that all items in your inventory have a numeric `price` field:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "item1",
  "timestamp": "1599391467000",
  "fields": [{"name": "price", "value": 69.0}]
}
```
Then for a ranking below:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "items": [
    {"id": "item1"},
    {"id": "item2"},
    {"id": "item3"} 
  ]
}
```
we can compute how different each item price compared to the median price across the whole ranking with the following configuration snippet:
```yaml
- name: price_diff
  type: diversity
  source: item.price # only item.* fields are accepted
  ttl: 90d # optional, when to expire tracked fields
```

For example, given the following item prices:
* p1: price=100
* p2: price=200
* p3: price=250
* p4: price=300
* p5: price=220

So for a ranking `[p1, p2, p3, p4, p5]` we compute a median value of 220, and then compute the difference:
* p1: price_diff=-120
* p2: price_diff=-20
* p3: price_diff=30
* p4: price_diff=80
* p5: price_diff=0

### Diversification over string fields

This type of diversification can be useful to see how different your items over low-cardinality fields like tags, colors, sizes and categories. Both string and string[] field types are supported.

When all your inventory items have a field `color` like in an example below:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "item1",
  "timestamp": "1599391467000",
  "fields": [{"name": "color", "value": "red"}]
}
```
Then for a ranking below:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "items": [
    {"id": "item1"},
    {"id": "item2"},
    {"id": "item3"} 
  ]
}
```
we can compute how different each item price compared to the median price across the whole ranking with the following configuration snippet:
```yaml
- name: price_diff
  type: diversity
  source: item.color # only item.* fields are accepted
  ttl: 90d # optional, when to expire tracked fields
```

The difference algorithm builds tag frequencies over the ranking (so `color -> count` in our example above), and then computes relative intersection between tags of item and tag frequencies. An example
* given a frequency of {red: 50%, green: 30%, blue: 20%}
* for an item having only red color, the score will be 50%.
* for an red-blue item, the score will be 50%+20%=70%