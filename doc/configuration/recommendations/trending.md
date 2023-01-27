# Trending items

`trending` recommendation model is used to highlight the most popular items on your site. But it's not about sorting items by popularity! Metarank can:
* combine multiple types of interactions: you can mix clicks and purchases with multiple weights.
* time decay: clicks made yesterday are much more important than clicks from the last months.
* multiple configurations: trending over the last week, and bestsellers over the last year.

## Configuration

A separate block in the `models` section:
```yaml
models:
  yolo-trending:
    type: trending
    weights:
      - interaction: click
        decay: 0.8 # optional, default 1.0 - no decay
        weight: 1.0 # optional, default 1.0
        window: 30d # optional, default 30 days
      - interaction: purchase
        decay: 0.95
        weight: 3.0
```

The config above defines a trending model, accessible over the `/recommend/yolo-trending` [API endpoint](../../api.md):
* the final item score combines click and purchase events
* purchase has 3x more weight than click
* purchase has less agressive time decay
* only the last 30 days of data are used

## Time decay and weight

The final score used to sort the items is defined by the following formula:
```
score = count * weight * decay ^ days_diff(now, timestamp)
```
If there's multiple interaction types defined, each per-type score is added together for the final score.

Such an unusual way of defining decay can allow a more granular control over the decaying. For example, that's how click importance is weighted for different `decay` values:

![decay with different options](../../img/decay.png)

We recommend setting a decay:
* within a range of 0.8-0.95 for 1-month periods.
* within a range of 0.95-0.99 for larger periods.
