# Similar items

A `similar` recommender model can give you items other visitors also liked, while viewing the item you're currently observing. 

Common use-cases for such model are:
* you-may-also-like recommendations on item page: the context of the recommendation is a single item you're viewing now.
* also-purchased widget on the cart page: the context of the recommendation is the contents of your card.

## Configuration

```yaml
  similar:
    type: als
    interactions: [click] # which interactions to use
    factors: 100 # optional, number of implicit factors in the model, default 100
    iterations: 100 # optional, number of training iterations, default 100
```

There are two important parameters in the configuration:
* `factors`: how many hidden parameters the model tries to compute. The more - the better, but slower. Usually defined within the rage of 50-500.
* `iterations`: how many factor refinements attempts are made. The more - the better, but slower. Normal range - 50-300.

Rule of thump - set these parameters low, and then increase slightly until training time becomes completely unreasonable.