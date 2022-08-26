# Click models

You may wonder why Metarank needs a `ranking` event (see [event schema](./event-schema.md) for details), compared to
other recommendation services only needing just interactions? The answer lies in the way Metarank does the 
ranking optimization.

## The clickthrough

During the initial bootstrapping process (when historical visitor activity data is processed), Metarank goes over all
ranking and interaction events and joins them into clickthroughs: a chain of rankings with the corresponding clicks
attached:
* each ranking is cached for at least 30 minutes.
* when interaction with this ranking is received, it is attached to the parent ranking.
* when no interactions happened for 30 minutes, the clickthrough is considered finalized.

The clickthrough is the smallest essential block of the training process:
* it shows what items were presented to the visitor
* and how the visitor interacted with them

## Supervised learning of ranking

The point of Metarank is to optimize a *metric*, making your ranking somehow better. But what metric can be used to
track the "goodness" of the ranking? It depends on your business goals, but metarank supports 
[multiple ranking models](configuration/supported-ranking-models.md) with the [NDCG](https://en.wikipedia.org/wiki/Discounted_cumulative_gain) 
as a metric to optimize for.

In a simple words, NDCG is a number between 0.0 and 1.0:
* when it's 1.0, then ranking was perfect: all items were sorted according to their true relevancy judgements. It's like
Google search results, but when the page you're looking for is always ranked #1.
* when it's 0.0, it's the worst possible ranking: all the most relevant items are in the end.

The question is where can we take these "true relevancy judgements" from? Here's where the Click model comes into play.

## Click model

Article [Click Modeling for eCommerce](https://tech.ebayinc.com/engineering/click-modeling-for-ecommerce/) from the eBay
tech blog provides a good example of different click models:

> The user searched for “Meaning of life”. A search result page (SRP) with 50 results was served back. The user 
> then clicked on result #2, and we never heard from him again.
>
> Consider the following two explanations:
>
> * The user looked at the SRP, read the snippet returned for result #1, then ignored it as irrelevant. The user 
> then moved to result #2, read the snippet, found it attractive, clicked through to the page, found the meaning
> of life in there, then stopped the search, satisfied with what he found.
> * The user glanced at the SRP, chose result #2 randomly, read the snippet, found it somewhat relevant, 
> clicked-through to the page, and found it completely irrelevant. Then his phone rang and he abandoned the search.
>
> According to the first explanation, result #2 was relevant to the userʼs search. According to the second explanation, 
> it wasnʼt. Both explanations (and many others) are possible. But are they equally likely?
>
> A click model helps us assign mathematical probabilities to every such explanation, enabling us to use millions of 
> scenarios to infer the likelihood of relevance for every search result against every search.
> 
> The simplest class of click models is called position models. Position models assume that search results have a 
> probability of being examined by the user that decays with the position of the result within the page. A click 
> depends on a result being examined and deemed relevant, so that P(click) = P(examined) * P(relevant), and P(examined) 
> is a decaying function of rank.
> 
> Cascade models are another class of click models, where the user is assumed to examine the results sequentially: 
> starting from the top, clicking on the first relevant result examined, and stopping the search immediately. 
> Here the probability of a click depends on the relevance of a result, as well as the irrelevance of all previous 
> results. This model doesnʼt account for abandoned searches, or searches with multiple clicks. 

Metarank uses a *cascade click model* to mark which items were "relevant" and which ones "irrelevant":
* An item which was clicked is relevant
* An item which was examined but not clicked is irrelevant
* An item which was not examined is ignored

## Clickthrough within a Cascade model

Applying the idea of a *cascade model* to the clickthroughs Metarank collects, it can distinguish between
relevant and irrelevant items and optimize ranking to maximize the relevance. From the ML perspective, the 
clickthrough looks like this:

<img src="img/ltr-table.png" />

* Each item in the ranking has a set of characterizing feature values.
* Some items have relevancy judgements that were extracted from interactions using the *cascade model*.

The underlying ML model slurps these labelled rankings and learns to distinguish between relevant and irrelevant
items using only their feature values. 

Next time the model sees an item with feature values looking similarly to something in the training dataset, it will
remember it and may assume how (ir)relevant this item should be based on already seen data.

Metarank supports multiple [ranking models](configuration/supported-ranking-models.md), but the LambdaMART model is probably a good start.

## Cascade model and Click-Through-Rate

Another perk of a *cascade model* is that it can be used for a more precise [CTR calculation](features/counters.md):
* CTR is literally clicks divided by impressions.
* It's obvious where you can take clicks from: there may be a special type of interaction.
* But what about the number of impressions?

When you presented a list of 100 items to the visitor, and the visitor clicked on #5, can we assume that items #6-#100 were
examined? According to the cascade model - it's unlikely, so CTR value may be significantly off for items rarely ranked
in the top.

To overcome this issue, Metarank can emit a synthetic impression event for items which were definitely examined by
the visitor:
```yaml
bootstrap:
  syntheticImpression:
    enabled: true
    eventName: impression # can be customized
```

It is enabled by default and generates interactions of type=impression after the clickthrough is finalized. You can
disable it in a case when you're able to generate impression events on the application side.

For example using *viewport tracking* in browser/mobile:
* When an item goes into the viewport, you emit the impression event manually 
* Specify the name of the event in `eventName` parameter and don't rely on Metarank heuristics.

Synthetic *impression* event generation can also be disabled if you're not using an impression anywhere across your feature set.

Interactions are generated properly even for multiple clicks that happened for a single ranking:
* ranking shown items A-B-C-D-E-F
* visitor clicked items B and D
* when the clickthrough is finalized, Metarank will emit impression interactions for items A-B-C-D.