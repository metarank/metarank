# API Warmup

When running Metarank in production, you may hit the cold-start problem:

* after a restart (due to redeployment/autoscaling) Metarank pod starts cold with empty caches,
* JVM also has not yet compiled most of the code,
* due to this, reranking latency of first N requests is too high.

To handle this Metarank (starting from v0.7.6) supports explicit API warmup:

* while training, sample N random but real reranking requests and persist them in store
* on API startup, replay this random traffic sample
* when replay done, bring up the API - so k8s readiness probe will be successful.

## Configuring warmup

API warmup is only supported for LambdaMART models and is configured per-model:

```yaml
models:
  my-super-model:
    type: lambdamart
    warmup:
      sampledRequests: 100 # how many requests sample during training
      duration: 5s # how long to replay the traffic during warmup
    # ...
```

Warmup is disabled by default, and you need to retrain your model if you trained it in Metarank prior to 0.7.6.

After the warmup is enabled, you will see the following log output when starting the API:

```
15:46:41.221 INFO  a.metarank.main.command.Standalone$ - model 'default' training finished
15:46:41.484 INFO  ai.metarank.main.command.Serve$ - warmup of model default: config=WarmupConfig(100,10 seconds) requests=100
15:46:42.486 INFO  ai.metarank.flow.PrintProgress$ - processed 900 warmup requests, perf=898rps GC=0.0% heap=6.63%/8.0G 
15:46:43.505 INFO  ai.metarank.flow.PrintProgress$ - processed 2100 warmup requests, perf=1178rps GC=0.0% heap=6.63%/8.0G 
15:46:44.548 INFO  ai.metarank.flow.PrintProgress$ - processed 3400 warmup requests, perf=1246rps GC=0.29% heap=6.63%/8.0G 
15:46:45.626 INFO  ai.metarank.flow.PrintProgress$ - processed 4700 warmup requests, perf=1206rps GC=0.0% heap=6.63%/8.0G 
15:46:46.683 INFO  ai.metarank.flow.PrintProgress$ - processed 6000 warmup requests, perf=1230rps GC=0.0% heap=6.63%/8.0G 
15:46:47.705 INFO  ai.metarank.flow.PrintProgress$ - processed 7300 warmup requests, perf=1273rps GC=0.0% heap=6.63%/8.0G 
15:46:48.729 INFO  ai.metarank.flow.PrintProgress$ - processed 8600 warmup requests, perf=1268rps GC=0.0% heap=6.63%/8.0G 
15:46:49.766 INFO  ai.metarank.flow.PrintProgress$ - processed 9900 warmup requests, perf=1255rps GC=0.0% heap=6.63%/8.0G 
15:46:50.789 INFO  ai.metarank.flow.PrintProgress$ - processed 11100 warmup requests, perf=1172rps GC=0.0% heap=6.63%/8.0G 
15:46:51.686 INFO  ai.metarank.main.command.Serve$ - 
                __                              __    
  _____   _____/  |______ ____________    ____ |  | __
 /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
|  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
|__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      \/     \/          \/           \/     \/     \/
15:46:51.686 INFO  ai.metarank.main.command.Serve$ - Starting API...
15:46:51.733 INFO  o.h.ember.server.EmberServerBuilder - Ember-Server service bound to address: [::]:8080
```