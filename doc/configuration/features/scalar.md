# Scalar feature extractors

The most typical use case of mapping data from incoming events to ML features is to use them as is, without any transformations.
Metarank has a set of basic extractors to simplify the process even more:
* `boolean`: take a true/false field and map it to 1 and 0
* `number`: take a number and use it as is
* `string`: do a one-hot encoding of low-cardinality string contents of the field

## Boolean and numerical extractors

Consider this type of incoming event, emitted by your backend system when a product goes in stock, or it's price changes:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "product1",
  "timestamp": "1599391467000",
  "fields": [
    {"name": "availability", "value": true},
    {"name": "price", "value": 69.0}
  ]
}
```

We can add the following extractor, so it will use the availability data for the ranking:
```yaml
- name: availability
  type: boolean
  scope: item
  field: item.availability // must be a boolean
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

In practice, you can use not only fields from item metadata events, but also from ranking.

An example for ranking events: 
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "banner_examined", "value": true}
  ],
  "items": [
    {"id": "product3", "fields": [{"name": "relevancy", "value": 2.0}]},
    {"id": "product1", "fields": [{"name": "relevancy", "value": 1.0}]},
    {"id": "product2", "fields": [{"name": "relevancy", "value": 0.1}]} 
  ]
}
```

So you can extract this `banner_examined` value using the following config:
```yaml
- name: banner_examined
  type: boolean
  scope: item
  field: ranking.banner_examined
```

It is also possible to extract per-item fields from the ranking event. For example, the `relevancy` field can be extracted this way:

```yaml
- name: relevancy
  type: number
  scope: item
  field: ranking.relevancy
```

Extracting fields from interaction events is not possible, as at the moment of ranking request happening, there
are no interactions happened yet, they will happen in the future.

## Numerical extractor

With the same approach as for boolean extractor, you can pull a `price` field out of a item metadata message into the
explicit feature:

```yaml
- name: price
  type: number
  field: item.price // must be a number
  scope: item
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

So the last price will be present in the set of ML features uses in the ranking.

It's also possible to use `user` fields in a case when you have some pre-existing information about the visitor
(for example, when visitor filled a form before). Then with this `user` event:
```json
{
  "event": "user",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "user": "user1",
  "timestamp": "1599391467000",
  "fields": [
    {"name": "age", "value": 30}
  ]
}
```
You can map the `age` field into a feature this way:
```yaml
- name: user_age
  type: number
  field: user.age // must be a number
  scope: user
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

## Vector extractor

Numerical vectors require special handling: their dimension is not statically known (or they can be empty), so we need to perform a set of transformations to reduce these to a static size used inside the ML model. 

For example, given an item with field `sizes: [10, 12, 13]`, we can use a `vector` extractor with the following configuration:

```yaml
- name: sizes
  type: vector
  field: item.sizes // must be a singular number or a list of numbers
  scope: item
  # which reducers to use. optional. Default: [min, max, size, avg]
  reduce: [first, last, min, max, avg, random, sum, size, euclidean_distance, vectorN] 
  refresh: 0s # optional, how frequently we should update the value, 0s by default
  ttl: 90d # optional, how long should we store this field
```

Supported reducers are:

* `first`/`last`/`min`/`max`/`random` - take first/last/min/max/random element of the list, or zero if empty.
* `avg`/`sum` - compute mean value or sum of values.
* `euclidean_distance` - compute a Euclidean distance of numerical vector, which is a squared root of sum of squares.
* `vectorN` - take first N items from the sequence, and pad remaining with zeroes. So `vector10` means a vector of 10 dimensions.

The `vectorN` reducer can also be useful if you compute embeddings (fields with constant predefined size) for your user/items as you can wrap them as ranking features directly. 
For example, when your item has a field `als_embedding: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]`, you can define a `vector` feature with `reduce: vector10` and the raw embedding will be short-circuited as a set of 10 separate numerical features for the ranking model.

## String extractors

With string values there is no easy way to map them into a finite set of ML features. But in a case when
the string has low cardinality (so there is a finite and low number of possible values), we have a couple of
options on how to treat these:
* [one-hot encoding](https://en.wikipedia.org/wiki/One-hot) to convert it to a number vector.
* Index encoding, which may work better when cardinality is high (e.g. > 10).

### One-hot encoding 
Imagine you have field `color: "red"` and there is only a small finite set of possible values for this field:
it can be either red, green or blue. So we can do the actual mapping in the following way:

```yaml
- name: color
  type: string
  scope: item
  encode: onehot // optional, default = index, options = onehot | index
  values: [red, green, blue]
  field: item.color // must be either a string, or array of strings
```

This snippet will emit the following ML feature group for a `color: "red"` input:
* color_red: 1
* color_green: 0
* color_blue: 0

The underlying string field can also be an array of strings like `color: ["red", "blue"]`, which will
toggle two bits instead of one in the resulting vector.

### Index encoding

One-hot encoding does not suit the cases, when your list has high cardinality (more than 10 distinct values, e.g. country list) as
the dimensionality of the training dataset can fly into the sky (you can have tens or even hundreds of model fields that represent just one feature).

For such use-cases it is much more effective to use index encoding.

* LightGBM backend supports proper split selection for categorical features. You can check out the [LightGBM documentation](https://lightgbm.readthedocs.io/en/latest/Features.html#optimal-split-for-categorical-features) for more details.
* XGBoost itself supports it, but it's not yet exposed in the Java wrapper, so it will treat index-encoded category as a 
regular numeric feature.

```yaml
- name: color
  type: string
  scope: item
  encode: index // optional, default = onehot, options = onehot | index
  values: [red, green, blue]
  field: item.color // must be either a string, or array of strings
```

This snippet will emit the following ML feature group for a `color: "green"` input:
* color: 2

Please note the **limitations** of the index encoder:
* Index encoder can only work with singular field values, so if it spots multiple colors, only the first 
value from the array will be used.
* empty values are encoded as *zero*, existing - starting from *one*.

### Index vs one-hot, what to choose?

In common scenarios:
* index encoding is always faster than one-hot one due to lower dataset dimensionality on tree-based backends
  (e.g. LightGBM and XGBoost)
* index encoding results in same or better NDCG metric on LightGBM backend, compared to one-hot
* on XGBoost usually results in the similar NDCG, but better result is not guaranteed.

If you're not sure what to choose - prefer index encoding, the default option.