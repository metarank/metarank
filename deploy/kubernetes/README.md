## Metarank Helm chart

Check out the docs on [Kubernetes deployment](https://docs.metarank.ai/reference/deployment-overview/kubernetes) for more details.

## TLDR:

1. Add metarank Helm repo
```shell
$> helm repo add metarank https://metarank.github.io/helm-charts
"metarank" has been added to your repositories

$> helm repo update
Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "metarank" chart repository
Update Complete. Happy Helming!
```

2. Pull the chart
```shell
$> helm pull metarank/metarank --untar
$> cd metarank
```

3. Edit the values.yaml for generic k8s-specific settings.
4. Update the metarank.conf [according to the main doc](https://docs.metarank.ai/reference/overview). Do not forget
to define a redis endpoint!
5. Install the chart:
```shell
helm install metarank . --set-file config=metarank.conf

NAME: metarank
LAST DEPLOYED: Tue Oct  4 15:32:47 2022
NAMESPACE: default
STATUS: deployed
REVISION: 1
```