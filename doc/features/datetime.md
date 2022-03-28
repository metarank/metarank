# Date and time extractors

## local_time extractor

This extractor is useful when you need to parse a local date-time, and get a time-of-day (or something similar) from there to 
catch a seasonality: maybe visitor behavior is different on morning and in evening? Given the event:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "localts", "value": "2021-12-03T10:15:30+01:00"}
  ],
  "items": [
    {"id": "item3", "relevancy":  2.0},
    {"id": "item1", "relevancy":  1.0},
    {"id": "item2", "relevancy":  0.5} 
  ]
}
```

and the following feature config:
```yaml
- name: time
  type: local_time
  parse: time_of_day // can be time_of_day/day_of_month/month_of_year/year/second
  source: ranking.localts // can only work with ranking event types, the field must be string with ISO-formatted zoned datetime
```

This extractor will pull the `10:15:30+01:00`, and map it into a `0..23.99` range, so one second before midnight will be
0.99, and midday will be 0.5.

This extractor requires a separate field and cannot use a global `timestamp` one as global is in UTC and has no information
about timezone.

Supported `parse` field values:
* time_of_day: local time in 0.0..23.99 range
* day_of_month: day of current month in 1..31 range
* month_of_year: current month in 1..12 range
* year: absolute current year value
* second: current local timestamp in seconds from epoch start

## item_age

Sometimes it can be useful to know how fresh is the item in the ranking? Consider the following metadata event:
```json
{
  "event": "metadata",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "product1", 
  "timestamp": "1599391467000",
  "fields": [
    {"name": "created_at", "value": "2021-12-03T10:15:30+01:00"}
  ]
}
```

It's possible to compute how much time passed from the `created_at` field value till now, with the following config
snippet:

```yaml
- name: freshness
  type: item_age
  source: metadata.created_at // can only work with metadata event types
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

The `source` field should have any of the following types:
* string, ISO8601 date+time, example: "2021-12-03T10:15:30+01:00"
* number, unixtime (number of seconds from epoch start), example `1648483661`
* string, unixtime as a string (so there will be no json number rounding), example: `"1648483661"`