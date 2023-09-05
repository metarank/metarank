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
* While testing Metarank locally in a [standalone mode](../deploy/standalone.md), as it has no external service dependencies.
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
  format: binary # optional, default=binary, possible values: json, binary

  cache:           # optional
    maxSize: 1024  # size of in-memory client-side cache for hot keys, optional, default=1024
    ttl: 1h        # how long should key-values should be cached, optional, default=1h

  pipeline:         # optional
    maxSize: 128    # batch write buffer size, optional, default=128
    flushPeriod: 1s # buffer flush interval, optional, default=1s
    enabled: true   # toggle pipelining, optional, default=true

  auth:                  # optional
    user: <username>     # optional when Redis ACL is disabled
    password: <password> # required if Redis server is run with requirepass argument
  
  tls:                   # optional, defaults to disabled
    enabled: true        # optional, defaults to false
    ca: <path/to/ca.crt> # optional path to the CA used to generate the cert, defaults to the default keychain
    verify: full         # optional, default=full, possible values: full, ca, off
    # full - verify both certificate and hostname
    # ca   - verify only certificate
    # off  - skip verification

  timeout:      # optional, defaults to 1s for all sub-timeouts
    connect: 1s # optional, defaults to 1s
    socket: 1s  # optional, defaults to 1s
    command: 1s # optional, defaults to 1s
  
  db: # optional, defaults to [0,1,2,3]: which redis dbs to use for persistence 
    state: 0  # can be used to co-locate multiple metarank instances
    values: 1 # on a single redis server
    rankings: 2
    models: 3
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

### TLS Support

Metarank supports connecting to Redis using TLS for transport encryption, but there is no way to autodetect
the type of connection. 

To connect to a TLS-enabled Redis server with self-signed certificate, you need to specify the CA used to sign
the certificate (for self-signed certs it will be the server certificate itself):

```yaml
enabled: true
ca: /tls/key.crt
```

To connect to a TLS-enabled Redis server with a certificate generated with default CA (for example, AWS ElastiCache Redis),
then you don't need to specify any custom CA:

```yaml
enabled: true
```

In a case when you have cert trust issues connecting to a TLS-enabled redis, you can downgrade the verification level.
Supported levels are:
* `full` - verify both certificate and hostname
* `ca` - verify only the certificate
* `off` - skip verification, trust all

An example:

```yaml
enabled: true
verify: off
```

### Authentication

`auth.user` and `auth.password` can control the credentials used to connect to Redis. As hardcoding the credentials into the config file is not usually considered secure, you can supply the credentials from environment variables:
* `METARANK_REDIS_USER` - only needed when Redis ACL is enabled.
* `METARANK_REDIS_PASSWORD` - the pre-shared password used to connect to the Redis instance.

Metarank's [Helm chart](../deploy/kubernetes.md) has a placeholder for the env variables passed inside the container inside Kubernetes. Usage example:
```yaml
env: 
  - name: METARANK_REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: redis-secret
        key: REDIS_PASSWORD
```



### State encoding formats

Metarank Redis persistence supports `json` and `binary` encoding formats for data stored in Redis:

* `json`: focused on readability and debugging simplicity. 
* `binary`: low-overhead binary encoding format, with better performance and smaller memory footprint.

`binary` format on typical datasets (like [RankLens](https://github.com/metarank/ranklens)) is ~2x faster 
and takes ~4x less RAM. We recommend it for larger datasets, when memory usage and associated costs are an
important factor.

### Redis support limitations

* Metarank requires Redis 6+ due to a lack of client-side caching support in 5.x
  * you can disable client caching altogether (for example, for managed Redis-compatible engines, like GCP Memorystore Redis) with `cache.maxSize: 0`.
* Redis Cluster is not yet supported; see ticket [568](https://github.com/metarank/metarank/issues/568) for the progress.