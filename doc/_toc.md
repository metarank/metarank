# Metarank docs

## Introduction

* [What is Metarank?](intro.md)
* [Quickstart](quickstart/quickstart.md)

## Reference
* [Installation](installation.md)
* [Event Format](event-schema.md)
  * [Timestamp formats](timestamp-formats.md)
* [API](api_schema.md)
* [Configuration](configuration.md)
  * [Feature extractors](feature-extractors.md) 
    * [Counters](features/counters.md)
    * [Date and Time](features/datetime.md)
    * [Generic](features/generic.md)
    * [Relevancy](features/relevancy.md)
    * [Scalars](features/scalar.md)
    * [Text](features/text.md)
    * [User Profile](features/user-session.md)
  * [Models](supported-ranking-models.md)
  * [Data Sources](data-sources.md)
  * [Persistence](persistence.md)
* [Deployment](deploy/overview.md) 
  * [Standalone](deploy/standalone.md)
  * [Docker](deploy/docker.md)
  * [Kubernetes](deploy/kubernetes.md)
* [Integrations](integrations/overview.md)
  * [Snowplow](integrations/snowplow.md) 

## Development

* [Changelog](changelog.md)
* [Building from source](dev/build.md)