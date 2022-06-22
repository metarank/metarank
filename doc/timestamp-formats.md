# Timestamp formats

All input Metarank events have a timestamp field, an example:
```json
{
  "type": "item",
  "id": "product1",
  "timestamp": "1599391467000",
  "fields": [
    {"name": "title", "value": "Nice jeans"}
  ]
}
```

The underlying JSON format has [a numerical precision issue with timestamps encoded as double numbers](https://www.techempower.com/blog/2016/07/05/mangling-json-numbers/),
so Metarank supports multiple ways of parsing timestamps:
* as string literal: number of millis from 1970-01-01 00:00:00 in UTC as a string, like `"1599391467000"`
* as [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) formatted zoned datetime string, 
in UTC: `"2022-06-22T11:21:39Z"`
* (not recommended) as JSON number, like `1599391467000`
