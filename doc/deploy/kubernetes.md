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
* bootstrap: import historical data, generate state snapshot. A batch distributed offline job, which reads all the input
data and generates intermediate files to continue processing in realtime.
* train: train the ML model using the data from the bootstrap job.
* upload: load the current system state (from the bootstrap job) to redis for the inference.
* api: start the API to perform the reranking.
* update: start the job refreshing ML feature values in realtime.

All the required manifests are present in the [deploy/k8s-manifests](../../deploy/k8s-manifests) directory.

## Installing flink-kubernetes-operator

As Metarank data processing jobs (like bootstrap, upload and update) are implemented as an Apache
Flink streaming jobs, you need to have a Flink cluster available. [Flink-kubernetes-operator](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/docs/concepts/overview/) simplifies
the process of creating session Flink clusters in k8s:
* there is a single cluster per single job,
* cluster is terminated when the job is completed,
* cluster created by the operator from the job CustomResourceDefinition,
* cluster state (on job crash/restart/upgrade) is handled by the operator automatically.

Check the [flink-k8s-operator helm installation instructions](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/docs/operations/helm/), 
but TLDR version is:
```bash
# use the latest RC2 version, until 1.0.0 is releases
$> helm repo add flink-operator-repo https://dist.apache.org/repos/dist/dev/flink/flink-kubernetes-operator-1.0.0-rc2/

"flink-operator-repo" has been added to your repositories

$> helm install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator

NAME: flink-kubernetes-operator
LAST DEPLOYED: Sun May 29 18:34:09 2022
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

After that, you may load the `FlinkDeployment`'s from Metarank to your k8s cluster, and all the cluster
management will be handled by the operator.

## S3 credentials

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

## Configuration file

All parts of Metarank depend on a config file, describing the way input events are mapped to ML features. You can
take a [sample config.yml](../sample-config.yml) as a source of inspiration for your own one and load it as a k8s
configmap:
```bash
kubectl create configmap metarank-config --from-file=path/to/config.yml
```

## Bootstrapping

The bootstraping process:
1. Takes historical click data describing your user activity
2. Replays the complete click history
3. Computes all ML feature value updates and emits training dataset.
4. Stores last ML feature values
5. Stores last system state used to compute these last ML feature values.

![bootstrap](../img/bootstrap.png)

The bootstrapping process is done by a [bootstrap.yml](../../deploy/k8s-manifests/bootstrap.yml) manifest. You may probably
need to edit it to set a S3 bucket used for state:
```yaml
apiVersion: flink.apache.org/v1beta1
kind: FlinkDeployment
metadata:
  name: metarank-bootstrap
spec:
  image: metarank:0.3.1
  flinkVersion: v1_15
  flinkConfiguration:
    taskmanager.numberOfTaskSlots: "1"
    state.savepoints.dir: s3://metarank-tmp/flink/savepoints
    state.checkpoints.dir: s3://metarank-tmp/flink/checkpoints
    high-availability: org.apache.flink.kubernetes.highavailability.KubernetesHaServicesFactory
    high-availability.storageDir: s3://metarank-tmp/flink/ha
  podTemplate:
    apiVersion: v1
    kind: Pod
```

Make sure that:
* `s3://metarank-tmp` bucket is replaced with the S3 bucket you own
* the `image` is the latest metarank version.
* config file refers only accessible S3 buckets (and no paths with `file://` schema) 

After that, perform the following:
```bash
kubectl create -f bootstrap.yml
```