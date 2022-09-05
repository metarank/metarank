# Running in production

These are general recommendations on running Metarank in a production environment.

![Production environment overview](/doc/img/production-deployment.png)

## Persistence

Metarank provides several [Persistence](/doc/configuration/persistence.md) options, however for production setup we recommend
using only [Redis persistance](/doc/configuration/persistence.md#redis-persistence) as it operates separately from running Metarank instances.

Redis does not depend on Metarank instances being re-deployed and should be configured with [disc backup](https://redis.io/docs/manual/persistence/).

At the moment, Metarank stores only processed events in Redis, so we recommend storing all events separately.

## API Serving

[Metarank CLI](/doc/cli.md) exposes several modes with which you can run Metarank: `standalone` and `serve`. 

Although `standalone` mode is great for development purposes, it can't be used for production deployment:
- standalone mode cannot be scaled as it's not possible to run several instances that point to the same database
- you cannot re-train the model without restarting Metarank

For production deployment, you should only use the `serve` mode. You can have as many `serve` instances as you need, depending on the load you have
and you can perform graceful restarts of Metarank with 0 downtime.

Resource consumption of the `serve` mode is relatively low as it performs minimal computations, so you can use cheaper nodes than
when training the model.

## Re-training

Metarank exposes a `train` mode that re-trains your model based on the calculated features. 
Training is a long-running process with high memory consumption, which depends on the amount of data that is stored, so we recommend
running this process on-demand. You can re-train your model once a week or once a month, so there's no need to keep a large instance online all the time. 
