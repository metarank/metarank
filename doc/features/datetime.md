# Date and time extractors

## time_of_day extractor

This extractor is useful when you need to parse a local date-time, and get a time-of-day from there to catch intra-day
seasonality: maybe visitor behavior is different on morning and in evening? Given the event:
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
  type: time_of_day
  source: ranking.localts // can only work with ranking event types, the field must be string with ISO-formatted zoned datetime
```

This extractor will pull the `10:15:30+01:00`, and map it into a `0..1` range, so one second before midnight will be
0.99, and midday will be 0.5.

This extractor requires a separate field and cannot use a global `timestamp` one as global is in UTC and has no information
about timezone.