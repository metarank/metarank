# Kubernetes deployment

## Requirements

For a distributed K8S deployment, metarank requires the following external services and tools to be already available:
1. Helm: used to install flink-k8s-operator
2. Redis: as an almost-persistent data store for inference. Can be also installed either inside k8s with helm, or as a
managed service like AWS ElastiCache Redis.
3. Distributed filesystem, like S3 or HDFS. Used for periodic snapshots, state storage and job upgrades. Most of existing
cloud providers have an S3-compatible object store (like GCE, AWS and DO), it should also work OK with Metarank.

## Overview

Metarank deployment is a multi-stage process, consisting of these steps:
* bootstrap: import historical data, generate state snapshot. A batch distributed offline job, which may process a 
large dataset.
* train: train the ML model using the data from the bootstrap job.
* upload: load the current system state to redis for the inference.
* api: start the API to perform the reranking.
* update: start the job refreshing ML feature values in realtime.

todo: picture

