### Installing flink-kubernetes-operator

As Metarank data processing jobs (like bootstrap, upload and update) are implemented as an Apache
Flink streaming jobs, you need to have a Flink cluster available.
[Flink-kubernetes-operator](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/docs/concepts/overview/)
simplifies the process of creating session Flink clusters in k8s:
* there is a single cluster per single job,
* cluster is terminated when the job is completed,
* cluster created by the operator from the job CustomResourceDefinition,
* cluster state (on job crash/restart/upgrade) is handled by the operator automatically.

Check the [flink-k8s-operator helm installation instructions](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/docs/operations/helm/),
but TLDR version is:
```bash
user@node ~ $ helm repo add flink-operator-repo https://dist.apache.org/repos/dist/release/flink/flink-kubernetes-operator-1.0.0/

"flink-operator-repo" has been added to your repositories

user@node ~ $ helm install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator

NAME: flink-kubernetes-operator
LAST DEPLOYED: Sun May 29 18:34:09 2022
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
```

After that, you may load the `FlinkDeployment`'s from Metarank to your k8s cluster, and all the cluster
management will be handled by the operator.