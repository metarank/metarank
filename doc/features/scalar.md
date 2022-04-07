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
  "item": "product1", // required
  "timestamp": "1599391467000", // required
  "fields": [
    {"name": "availability", "value": true},
    {"name": "price", "value": 69.0},
  ]
}
```

We can add the following extractor, so it will use the availability data for the ranking:
```yaml
- name: availability
  type: boolean
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
    {"id": "product3", "relevancy":  2.0},
    {"id": "product1", "relevancy":  1.0},
    {"id": "product2", "relevancy":  0.5} 
  ]
}
```

So you can extract this `banner_examined` value using the following config:
```yaml
- name: banner_examined
  type: boolean
  field: ranking.banner_examined
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
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

## String extractors

With string values there is no easy way to map them into a finite set of ML features. But in a case when
the string has low cardinality (so there is a finite and low number of possible values), we can do a
(one-hot encoding)[https://en.wikipedia.org/wiki/One-hot] to convert it to a series of numbers.

Imagine you have field `platform: "mobile"` and there is only a small finite set of possible values for this field:
it can be either mobile, desktop or tablet. So we can do the actual mapping in the following way:

```yaml
- name: platform
  type: string
  values: [mobile, desktop, tablet]
  field: ranking.platform // must be either a string, or array of strings
```

This snippet will emit the following ML feature group for a `platform: "mobile"` input:
* platform_mobile: 1
* platform_desktop: 0
* platform_tablet: 0

The underlying string field can be also an array of strings like `platform: ["mobile", "tablet"]`, which will
toggle two instead of one bit in the resulting vector.

As with number/boolean extractor, there is a limitation on extracting fields from interaction events: it is not possible, 
as at the moment of ranking request happening, there are no interactions happened yet, they will happen in the future.

## Number extractor

