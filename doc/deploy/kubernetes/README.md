# Kubernetes deployment

## Intro

This document describes how you can run Metarank inside Kubernetes. In the following diagram an overall system
structure is shown:
![system diagram](../../img/data-flow.png)

Metarank deployment is a multi-stage process, consisting of these steps:
* bootstrap: import historical data, generate state snapshot. A batch distributed offline job, which reads all the input
  data and generates intermediate files to continue processing in realtime.
* train: train the ML model using the data from the bootstrap job.
* upload: load the current system state (from the bootstrap job) to redis for the inference.
* api: start the API to perform the reranking.
* update: start the job refreshing ML feature values in realtime.

## Requirements

For a distributed K8S deployment, metarank requires the following external services and tools to be already available:
1. Helm: used to install flink-k8s-operator
2. Redis: as an almost-persistent data store for inference. Can be also installed either inside k8s with helm, or as a
   managed service like AWS ElastiCache Redis.
3. Distributed filesystem or block storage. Good examples are S3, HDFS and CSI-based storage providers for Kubernetes.
   Used for periodic snapshots, state storage and job upgrades. Most of existing cloud providers have an S3-compatible
   object store (like GCE, AWS and DO), it should also work OK with Metarank.

### Installing flink-kubernetes-operator

As Metarank data processing jobs (like bootstrap, upload and update) are implemented as an Apache
Flink streaming jobs, you need to have a Flink cluster available.
[Flink-kubernetes-operator](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/docs/concepts/overview/)
simplifies the process of creating Flink session clusters in k8s. Metarank-specific operator installations instructions
are available in [a separate chapter](flink-operator.md).

### S3 credentials

Metarank needs access to a S3-compatible block storage with the corresponding AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
keys. You can take [secret.yml manifest](../../deploy/k8s-manifests/secret.yaml) as a template:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: metarank-keys
type: Opaque
data:
  AWS_ACCESS_KEY_ID: "<base64 encoded key>"
  AWS_SECRET_ACCESS_KEY: "<base64 encoded secret>"
```
You can either fill your base64-encoded key and secret there and load it with `kubectl apply -f secret.yml`, or create
from command line:
```bash
kubectl create secret generic metarank-keys \
  --from-literal=AWS_ACCESS_KEY_ID=supersecret \
  --from-literal=AWS_SECRET_ACCESS_KEY=topsecret
```

All the required manifests are present in the [deploy/k8s-manifests](../../deploy/k8s-manifests) directory.


### Configuration file

All parts of Metarank depend on a config file, describing the way input events are mapped to ML features. You can
take a [sample config.yml](../sample-config.yml) as a source of inspiration for your own one and load it as a k8s
configmap:
```bash
kubectl create configmap metarank-config --from-file=path/to/config.yml
```

### Bootstrapping

The bootstraping process:
1. Takes historical click data describing your user activity
2. Replays the complete click history
3. Computes all ML feature value updates and emits training dataset.
4. Stores last ML feature values
5. Stores last system state used to compute these last ML feature values.

![bootstrap](../../img/bootstrap.png)

## Distributed and local bootstrap

Both Bootstrap and Train jobs can be run as inside in Kubernetes in a distributed fashion,
and as local apps:
* distributed mode linearly scales and can handle large datasets, but requires
more steps to set up. If your dataset is larger than 10GB, then you should consider
this approach.
* local mode does not require any extra setup and runs on your local dev computer, but maybe slow 
for large input datasets.

Later in this document we assume:
* bootstrap and training happens locally.
* feature update job and reranking API are run within kubernetes.

For a detailed description on setting up a distributed training job, check out [this chapter.](distributed-bootstrap.md)

### Local bootstrap

Local bootstrap requires you to have your `bootstrap.workdir` hosted on a distributed FS like S3. An example config file
(reduced version of a [ranklens demo config file](../../../src/test/resources/ranklens/config.yml):
```yaml
bootstrap:
  source:
    type: file
    path: file:///home/user/events/ # event files can be local or S3, they're used only on bootstrap
  workdir: s3://metarank-tmp/ranklens/bootstrap/ # this should be S3/HDFS

inference:
  port: 8080
  host: "0.0.0.0"
  source:
    type: rest
  state:
    type: redis
    host: localhost
    format: protobuf

models:
  xgboost:
    type: lambdamart
    path: s3://metarank-tmp/ranklens/xgboost.model # should be S3/HDFS to allow API to pick it up
    backend:
      type: xgboost
      iterations: 10
    weights:
      click: 1
    features:
      - popularity
features:
  - name: popularity
    type: number
    scope: item
    source: metadata.popularity
```

For a local bootstrap, S3 access credentials need to be present as env variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`.
To run the bootstrap, do the following:
```bash
user@node ~ $ java -jar metarank.jar bootstrap path/to/config.yml
```

After the job completes (usual estimated time is 1-2 minutes for datasets within 1Gb), you can validate that all the 
output files are present on the workdir S3 directory in the same way it's [done for a distributed bootstrap](distributed-bootstrap.md#validating-output).