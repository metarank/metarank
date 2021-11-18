## Running the metarank CLI

Metarank is distributed as a fat-jar app (with all the dependencies bundled together in a single file), and
requires only a JDK 11+ to run:

```shell
java -jar metarank-X.X.X.jar <mode> --config <conf.yml>
```

Metarank has multiple running modes:
* *train*: process historical telemetry stream offline and train the ML model
* *feature_update*: receive online telemetry stream from customers and update feature values
* *inference*: receive online reranking requiest via API and run the model
* *local*: bundles feature_update and inference into a single application, useful for local playground runs

For all the modes you need to have a valid config file available, see [here for details](03_configuration.md).

### Training

```shell
java -jar metarank-X.X.X.jar train --config <conf.yml>
```

TODO

## Feature update

TODO

### Online inference

TODO
