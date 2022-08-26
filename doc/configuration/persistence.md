# Persistence

Metarank supports two possible persistence modes:
* [Memory](persistence.md#memory-persistence): ephemeral; all state is in RAM. 
* [Redis](persistence.md#redis-persistence): state persisted in remote Redis.

Persistence mode is configured by the optional `state` section in the [configuration file](overview.md).
By default, if the section is not defined, Metarank uses [memory persistence](persistence.md#memory-persistence).

## Memory persistence

Memory persistence is no persistence at all: the complete Metarank state is stored only in RAM, is 
ephemeral, and will be entirely lost on each service restart. 

Nevertheless, memory persistence can be useful:
* While testing Metarank locally in a [standalone mode](deploy/standalone.md), as it has no external service dependencies.
* As a staging env to validate configuration changes before going to production.

To configure memory persistence, use the `type: memory` option:
```yaml
state:
  type: memory
```

## Redis persistence

Metarank can use [Redis 6+](https://redis.io) as a persistence method. To enable it, use the following 
[config file](overview.md) snippet:
```yaml
state:
  type: redis
  host: localhost
  port: 6379

  cache:           # optional
    maxSize: 1024  # size of in-memory client-side cache for hot keys, optional, default=1024
    ttl: 1h        # how long should key-values should be cached, optional, default=1h

  pipeline:         # optional
    maxSize: 128    # batch write buffer size, optional, default=128
    flushPeriod: 1s # buffer flush interval, optional, default=1s
```

Redis persistence is sensitive to network latencies (as it needs to perform a couple of round-trips on each event), 
hence Metarank leverages a couple of Redis performance optimization strategies:
* [Pipelining](https://redis.io/docs/manual/pipelining/): all write operations are batched together and sent all at once
* [Client-side caching](https://redis.io/docs/manual/client-side-caching/): read cache for hot keys with server-assisted 
invalidation.

A note on optional cache & pipelining related settings:
* Metarank has a separate cache per underlying feature type (like scalar/counter/map/etc, 10 total), so 
`cache.maxSize` is set per cache type, so keep in mind an implicit multiplication: default value `1024` in reality
means `10240`.
* `cache.ttl` defines expiration interval after last read, so hot features may be cached almost indefinitely. The problem 
of stale cache values is solved with [server-assisted invalidation](https://redis.io/docs/manual/client-side-caching/): 
Redis server sends a notification to Metarank when key value was changed by someone else.
* `pipeline.maxSize` going above `128` is usually giving no benefit on low latencies (e.g. when Redis server is located 
in the same datacenter/AZ)
* `pipeline.flushPeriod` controls the level of "eventualness" in the overall eventual consistency. With values 
larger than `10` seconds, a second Metarank instance may not see write buffered in a first instance.

### Redis support limitations

* Metarank requires Redis 6+ due to a lack of client-side caching support in 5.x
* Redis Cluster is not yet supported; see ticket [568](https://github.com/metarank/metarank/issues/568) for the progress.