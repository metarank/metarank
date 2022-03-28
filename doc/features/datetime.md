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
      {"name": "localts", "value": "2011-12-03T10:15:30+01:00"}
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