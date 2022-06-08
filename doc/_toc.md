# Metarank docs

## Introduction

* [What is Metarank?](01_intro.md)
* [Technical overview](02_tech_overview.md)
* [Configuration](03_configuration.md)
  * [API Schema](api_schema.md)
  * [Data Sources](data-sources.md)
  * [Scopes](scopes.md)
* [Feature extractors](feature_extractors.md)
  * [Counters](features/counters.md)
  * [Date and Time](features/datetime.md)
  * [Generic](features/generic.md)
  * [Relevancy](features/relevancy.md)
  * [Scalars](features/scalar.md)
  * [Text](features/text.md)
  * [User Profile](features/user-session.md)

## Use cases
* [Personalized recommendations](https://medium.com/metarank/personalizing-recommendations-with-metarank-f2644112536b)

## Deployment

* [Standalone](supported-platforms.md)
  * [CLI options](deploy/cli-options.md)
  * [Docker](deploy/docker.md)
* [Kubernetes](deploy/kubernetes/README.md)
  * [Distributed bootstrap](deploy/kubernetes/distributed-bootstrap.md)
  * [Flink operator](deploy/kubernetes/flink-operator.md)

## Advanced topics

* [Click models](click-models.md)
* [Ranking models](supported-ranking-models.md)