# Persistence

Metarank supports two possible persistence modes:
* Redis: 

Persistence mode is configured by the optional `state` section in the [configuration file](configuration.md).
By default, if the section is not defined, Metarank uses [memory persistence](persistence.md#memory-persistence).

## Memory persistence

"Memory persistence" is actually no persistence at all: the complete Metarank state is stored in RAM, is 
ephemeral and lost completely on each service restart. 

Nevertheless, memory persistence cat be useful:
* while testing Metarank locally, as it has no external service dependencies
* as a staging env to validate configuration changes before going to production

To configure memory persistence, use the `type: memory` option:
```yaml
state:
  type: memory
```

## Redis persistence

