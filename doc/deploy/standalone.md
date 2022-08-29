# Standalone

As a Java application, Metarank can be run locally either as a JAR-file or Docker container, there is no need for
Kubernetes and AWS to start playing with it. Check out the [installation guide](../installation.md) for detailed
setup instructions.

## Running modes

Metarank has multiple running modes:
* `import` - import historical clickthroughs to 
* `train`
* `serve`
* `standalone` - which is a shortcut for `import`, `train` and `serve` jobs run together.

Metarank's standalone mode is made to simplify the initial onboarding on the system:
* it's a shortcut to run [`import`, `train` and `serve`](cli.md) tasks all at once,
* with [memory persistence](../configuration/persistence.md#memory-persistence) it can process large clickthrough histories almost 
instantly, speeding up the process of altering set of used features in a [config file](../configuration/overview.md)

