# Metarank docs

## Introduction

* [What is Metarank?](intro.md)
* [Quickstart](quickstart/quickstart.md)

## Reference
* [Installation](installation.md)
* [Event Format](event-schema.md)
  * [Timestamp formats](timestamp-formats.md)
* [API](api.md)
* [CLI](cli.md)
* [Configuration](configuration/overview.md)
  * [Feature extractors](configuration/feature-extractors.md) 
    * [Counters](configuration/features/counters.md)
    * [Date and Time](configuration/features/datetime.md)
    * [Generic](configuration/features/generic.md)
    * [Relevancy](configuration/features/relevancy.md)
    * [Scalars](configuration/features/scalar.md)
    * [Text](configuration/features/text.md)
    * [User Profile](configuration/features/user-session.md)
  * [Models](configuration/supported-ranking-models.md)
  * [Data Sources](configuration/data-sources.md)
  * [Persistence](configuration/persistence.md)
* [Deployment](deploy/overview.md) 
  * [Standalone](deploy/standalone.md)
  * [Docker](deploy/docker.md)
  * [Kubernetes](deploy/kubernetes.md)
* [Integrations](integrations/overview.md)
  * [Snowplow](integrations/snowplow.md) 

## Development

* [Changelog](changelog.md)
* [Building from source](dev/build.md)