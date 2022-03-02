# Counters

Event count is a nice and simple signal to affect ranking. But implementation-wise it is quite tricky:
* counters are constantly changing in time, so to do proper model training and backtesting, you need to maintain
  a historical view on counter values. For example, if you're counting clicks over products, you need to know the
  resulting number on each point in time when each click happened.
* global and always incrementing counters may work well for user/session scoped things, but counting clicks over 
  products requires time windowing, as having 100 clicks over the whole lifetime is completely different from  
  having the same 100 clicks, but for yesterday.

In Metarank, there are two different types of counters implemented:
* interaction_counter: a simple global always incrementing counter on interactions. Good for counting number of clicks within 
  session, cart size and so on.
* window_counter: when you need to time-frame all the events. Good for item popularities.

## Interaction counter

Interaction counter is configured in a following way:
```yaml
- name: click_count
  scope: item // you can also count actions by a user/session
  interaction: click
  refresh: 60s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field if there were no updates
```

Refresh field can be useful for counters when you don't want to update the value used for inference super frequently
and want to limit the write throughput to feature store.

## Windowed counter

Windowed counter has the same semantics as interaction one, but is configured in a different way:
```yaml
- name: clicks
  type: window_count
  interaction: click
  scope: item
  bucket_size: 24h // make a counter for each 24h rolling window
  windows: [7, 14, 30, 60] // on each refresh, aggregate to 1-2-4-8 week counts
  refresh: 60s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field if there were no updates
```

So this feature extractor will emit a group of following features:
* clicks_7: 12
* clicks_14: 34
* clicks_30: 70
* clicks_60: 124

These feature values will be updated at least every 60 seconds.

Window counters are implemented as a circular buffer of counters. There is an each separate counter for each time bucket,
and there are as many separate time buckets as max window size. As counters are updated frequently, it can be computationally 
expensive to refresh them on each interaction, so it's usually worth it to limit the refresh rate to something reasonable
like 10 minutes.

There is also a way to combine multiple windowed counters into a (rate)[./rate.md] to make streaming computation of CTR/Conversion rates
easier.