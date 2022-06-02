# Distributed bootstrap

## Prerequisites

The bootstrap job assumes that you have successfully:
* added S3 access credentials with `metarank-keys` Secret
* imported metarank config file as a `metarank-config` ConfigMap
* installed the `flink-kubernetes-operator`

## Running the bootstrap job

The bootstrapping process is done by a [bootstrap.yml](../../deploy/k8s-manifests/bootstrap.yml) manifest.

The manifest requires setting a couple of parameters required to S3:
* `spec.flinkConfiguration.state.savepoints.dir`: should point to a S3/HDFS directory to store Flink savepoints.
  A good example is `s3://<bucket>/metarank/savepoints`. Savepoints are used in a case of job upgrade or reconfiguration
  without loosing any data.
* `spec.flinkConfiguration.state.checkpoints.dir`: pointing to a S3/HDFS directory to store periodic job checkpoints - these
  are used to recover the job from failures.
* `spec.job.parallelism`: how many workers should process your data in parallel.

You may probably need to edit it to set a S3 bucket used for state:
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
    state.savepoints.dir: s3://your-bucket-here/metarank/savepoints
    state.checkpoints.dir: s3://your-bucket-here/metarank/checkpoints
    high-availability: org.apache.flink.kubernetes.highavailability.KubernetesHaServicesFactory
    high-availability.storageDir: s3://your-bucket-here/metarank/ha
  podTemplate:
    apiVersion: v1
    kind: Pod
    
    <...>
```

After updating the bootstrap manifest, perform the following:
```bash
user@node ~ $ kubectl create -f bootstrap.yaml

flinkdeployment.flink.apache.org/metarank-bootstrap created
```

After that metarank will start performing the bootstrap. For the external observer point of view, these things will happen:
* `flink-kubernetes-operator` will spawn a main pod of Metarank (JobManager in the Flink terms)
* Metarank JobManager will start as many child worker pods (TaskManagers in Flink terms) to process the input dataset.
* Metarank TaskManagers will do the distributed bootstrap and generate the data files on S3 dir.

By doing an `kubectl get pods` you will eventually see a set of child TaskManagers:
```bash
user@node ~ $ kubectl get pods

NAME                                        READY   STATUS              RESTARTS       AGE
flink-kubernetes-operator-dfbbbcd6b-j664t   2/2     Running             0              20h
metarank-bootstrap-6459b6967c-7bgfr         1/1     Running             0              96s
metarank-bootstrap-taskmanager-1-1          0/1     ContainerCreating   0              2s
metarank-bootstrap-taskmanager-1-2          0/1     ContainerCreating   0              2s
```

## Validating output 

When the main bootstrap job completes, in the workdir directory (see `bootstrap.workdir` [config
option](../../sample-config.yml) for details) you will see three set of files:
```bash
user@node ~ $ aws s3 ls --recursive s3://somebucket/ranklens/
2022-06-02 14:21:23   29436654 ranklens/bootstrap/dataset-xgboost/2022-06-02--12/dataset-77996949-cd54-4966-891c-f32f6df05176-0.json
2022-06-02 14:20:11      82900 ranklens/bootstrap/features/item/budget/values-5864ad13-c458-4c10-ba10-e7de8b108ff3-0.pb
2022-06-02 14:20:58      78598 ranklens/bootstrap/features/item/budget/values-5d383114-8312-47ac-b0a2-c28ae5fd8613-0.pb
2022-06-02 14:20:57     123783 ranklens/bootstrap/features/item/ctr_click/values-35ac4731-297a-4f0f-babd-f38a7003bf05-0.pb
2022-06-02 14:20:13     131349 ranklens/bootstrap/features/item/ctr_click/values-d5d5c11c-f6af-4027-9d7f-135ccba8a0f6-0.pb
2022-06-02 14:20:57     132233 ranklens/bootstrap/features/item/ctr_impression/values-21ca1bac-9345-4031-ace8-234c33c4e244-0.pb
2022-06-02 14:20:15     138526 ranklens/bootstrap/features/item/ctr_impression/values-b863b961-683e-4c2d-b6e4-8653216db2fa-0.pb
2022-06-02 14:20:57     140716 ranklens/bootstrap/features/item/day_item_click_count/values-68626500-9223-4ed7-bdd7-6aeb06cca762-0.pb
2022-06-02 14:21:19    7603254 ranklens/bootstrap/savepoint/49650b7f-e495-4f37-a1ed-d94cdc02a14f
2022-06-02 14:17:41    3668308 ranklens/bootstrap/savepoint/5ab5e5bf-00bd-47ca-9ff3-1b1c7db6bc2f
2022-06-02 14:17:40    3738162 ranklens/bootstrap/savepoint/9da33833-9d9e-4eed-b5f3-de6e19639ab6
2022-06-02 14:16:40    1609930 ranklens/bootstrap/savepoint/9db79952-5bc2-45b1-b744-2956207747a7
2022-06-02 14:21:22      19882 ranklens/bootstrap/savepoint/_metadata
```

So there are:
* `dataset-<modelname>/` files in an intermediate format suitable for ML model training
* `features/*` latest feature values
* `bootstrap/` state snapshot to continue computing feature values in online job.