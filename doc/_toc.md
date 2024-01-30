# Metarank docs

## Introduction

* [What is Metarank?](intro.md)
* [Quickstart](quickstart/quickstart.md)
* [Performance](performance.md)

## Guides

* [Search](guide/search/index.md)
  * [Reranking with cross-encoders](guide/search/cross-encoders.md)

## Reference
* [Installation](installation.md)
* [Event Format](event-schema.md)
  * [Timestamp formats](timestamp-formats.md)
* [API](api.md)
* [Command-line options](cli.md)
* [Configuration](configuration/overview.md)
  * [Feature extractors](configuration/feature-extractors.md) 
    * [Counters](configuration/features/counters.md)
    * [Date and Time](configuration/features/datetime.md)
    * [Generic](configuration/features/generic.md)
    * [Relevancy](configuration/features/relevancy.md)
    * [Scalars](configuration/features/scalar.md)
    * [Text](configuration/features/text.md)
    * [User Profile](configuration/features/user-session.md)
    * [Diversification](configuration/features/diversity.md)
  * [Recommendations](configuration/recommendations.md)
    * [Trending items](configuration/recommendations/trending.md)
    * [Similar items](configuration/recommendations/similar.md)
    * [Semantic similarity](configuration/recommendations/semantic.md)
  * [Models](configuration/supported-ranking-models.md)
  * [Data Sources](configuration/data-sources.md)
  * [Persistence](configuration/persistence.md)
* [Deployment](deploy/deployment-overview.md) 
  * [Standalone](deploy/standalone.md)
  * [Docker](deploy/docker.md)
  * [Kubernetes](deploy/kubernetes.md)
  * [Prometheus metrics export](deploy/prometheus.md)
  * [Custom logging](deploy/custom-logging.md)
* [Integrations](integrations/integrations-overview.md)
  * [Snowplow](integrations/snowplow.md) 

## How-to
* [Automated ML model retraining](howto/model-retraining.md)
* [Automatic feature engineering](howto/autofeature.md)
* [Running in production](dev/production-recommendations.md)

## Development
* [Changelog](changelog.md)
* [Building from source](dev/build.md)

### Doc versions
* [0.7.6 (stable)](https://docs.metarank.ai)
* [master (unstable)](https://metarank.gitbook.io/metarank-docs-unstable/)