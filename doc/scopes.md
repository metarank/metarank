# Scoping

Metarank uses scopes for computing and storing feature values. Feature values are stored not as atomic 
values but as a time-based changelog, where *scope* is a unique key for this particular 
instance of the ML feature. 

Metarank has four predefined types of scopes:

* *tenant*: the feature is scoped globally for a complete online store, for example, air temperature outside.
* *item*: take the item id for a feature changelog, for example: an item popularity.
* *user*: use user id as a unique id, for example: number of past sessions.
* *session*: use session id as a unique id, for example: number of items in a cart right now.

Scoping is also used for optimizing feature grouping while doing inference and model training: 
we compute all the ML features defined in the configuration for all candidate items, but some of the features are 
constant for the full ranking: if you have a site-global feature of air temperature outside, then it will be 
the same for all the items in the ranking, so no need to repeat the computation again.

While building training data for the offline bootstrapping stage, this optimization technique can save a lot of raw processing power.

### Scoping example

All ML feature extractors in Metarank have a `scope` option in their configuration. For example:

```yaml
- name: popularity
  type: number
  field: item.popularity // must be a number
  scope: item
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

The `scope: item` means that the extracted popularity field from item metadata should be stored by an item identifier key.