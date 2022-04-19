# Scoping

Metarank uses the idea of scoping while computing and storing feature values: as we store them not as atomic 
values but more like a time-based changelog, the scope is a unique key of this particular 
instance of the ML feature. Metarank has four predefined types of scopes:

* Tenant: the feature is scoped globally for a complete online store, for example, air temperature outside.
* Item: take the item id for a feature changelog. An example: a product price.
* User: use user id as an unique id, for example: number of past sessions.
* Session: use session id as an unique id, for example: number of items in a cart right now.

Scoping is also used later as a way to optimize feature grouping while doing inference and model training: 
we compute all the ML features defined in the config for all candidate items, but some of the features are 
constant for the full ranking: if you have a site-global feature of air temperature outside, then it will be 
the same for all the items in the ranking, so no need to repeat the computation again.

While building training data on the offline bootstrapping stage, this optimization technique can save 
us a lot of raw processing power.

### Scoping example

All ML feature extractors in Metarank have a `scope` option in their configuration. For example:

```yaml
- name: price
  type: number
  field: item.price // must be a number
  scope: item
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

The `scope: item` means that the extracted price field from item metadata should be stored by an item identifier key.