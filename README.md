<h1 align="center">
    <a style="text-decoration: none" href="https://www.metarank.ai">
      <img width="120" src="https://raw.githubusercontent.com/metarank/metarank/master/doc/img/logo.svg" />
      <p align="center">Metarank: real time personalization as a service</p>
    </a>
</h1>
<h2 align="center">
  <a href="https://docs.metarank.ai">Docs</a> | <a href="https://metarank.ai">Website</a> | <a href="https://metarank.ai/slack">Community Slack</a> | <a href="https://blog.metarank.ai">Blog</a> | <a href="https://demo.metarank.ai">Demo</a>
</h2>

[![CI Status](https://github.com/metarank/metarank/workflows/Tests/badge.svg)](https://github.com/metarank/metarank/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/metarank/metarank)
![Last release](https://img.shields.io/github/release/metarank/metarank)
[![Join our slack](https://img.shields.io/badge/Slack-join%20the%20community-blue?logo=slack&style=social)](https://metarank.ai/slack)


## What is Metarank?

Metarank is a personalization service that can be easily integrated into existing systems and used to personalize different types of content. 

Like Instagram’s personalized feed that is based on the posts that you’ve seen and liked, Facebook’s new friends recommendation widget or Amazon’s personalized results, you can add personalization to your application. You can combine different features, both user-based like location or gender and item-based like tags with different actions: clicks, likes, purchases to create a personalized experience for your users.

Thanks to Metarank’s simple API and YAML configuration, you don’t need any prio machine learning experience to start improving your key metrics and run experiments.

![Demo](./doc/img//demo.gif)

Personalization is showing items that have unique order for each and every user. Personalization can be done based on user properties: location, gender, preferences and user actions: clicks, likes and other interactions. You can see personalized widgets everywhere: Facebook uses personalization to suggest you new friends and show posts that will most likely get your attention first; AirBnB uses personalization for their experiences offering, suggesting new experiences based on your location and previous actions. 

With Metarank you implement similar systems thanks to flexible configuration and keep control of your user data.

## Metarank in One Minute

Let us show how you can start personalizing content in just under a minute (depends on your internet speed!). 

### Step 1: Prepare data

We will use the [ranklens dataset](https://github.com/metarank/ranklens), which is used in our [Demo](https://demo.metarank.ai), so just download the data file

```bash
curl -O -L https://github.com/metarank/metarank/raw/master/src/test/resources/ranklens/events/events.jsonl.gz
```

### Step 2: Prepare configuration file

We will again use the configuration file from our [Demo](https://demo.metarank.ai). It utilizes in-memory store, so no other dependencies are needed.


```bash
curl -O -L https://raw.githubusercontent.com/metarank/metarank/master/src/test/resources/ranklens/config.yml
```

### Step 3: Start Metarank!

With the final step we will use Metarank’s `standalone` mode that combines training and running the API into one command:

```bash
docker run -i -t -p 8080:8080 -v $(pwd):/opt/metarank metarank/metarank:latest standalone --config /opt/metarank/config.yml --data /opt/metarank/events.jsonl.gz
```

You will see some useful output while Metarank is starting and grinding through the data. Once this is done, you can send requests to `localhost:8080` to get personalized results.

Here we will interact with several movies by clicking on one of them and observing the results. 

> First, let's see the initial output provided by Metarank without before we interact with it

```bash
# get initial ranking for some items
curl http://localhost:8080/rank/xgboost \
    -d '{
    "event": "ranking",
    "id": "id1",
    "items": [
        {"id":"72998"}, {"id":"67197"}, {"id":"77561"},
        {"id":"68358"}, {"id":"79132"}, {"id":"103228"}, 
        {"id":"72378"}, {"id":"85131"}, {"id":"94864"}, 
        {"id":"68791"}, {"id":"93363"}, {"id":"112623"}
    ],
    "user": "alice",
    "session": "alice1",
    "timestamp": 1661431886711
}'

# {"item":"72998","score":0.9602446652021992},{"item":"79132","score":0.7819134441404151},{"item":"68358","score":0.33377910321385645},{"item":"112623","score":0.32591281190727805},{"item":"103228","score":0.31640256043322723},{"item":"77561","score":0.3040782705414116},{"item":"94864","score":0.17659007036183608},{"item":"72378","score":0.06164568676567339},{"item":"93363","score":0.058120639770243385},{"item":"68791","score":0.026919880032451306},{"item":"85131","score":-0.35794106000271037},{"item":"67197","score":-0.48735167237049154}
```

```bash
# tell Metarank which items were presented to the user and in which order from the previous request
# optionally, we can include the score calculated by Metarank or your internal retrieval system
curl http://localhost:8080/feedback \
 -d '{
  "event": "ranking",
  "fields": [],
  "id": "test-ranking",
  "items": [
    {"id":"72998","score":0.9602446652021992},{"id":"79132","score":0.7819134441404151},{"id":"68358","score":0.33377910321385645},
    {"id":"112623","score":0.32591281190727805},{"id":"103228","score":0.31640256043322723},{"id":"77561","score":0.3040782705414116},
    {"id":"94864","score":0.17659007036183608},{"id":"72378","score":0.06164568676567339},{"id":"93363","score":0.058120639770243385},
    {"id":"68791","score":0.026919880032451306},{"id":"85131","score":-0.35794106000271037},{"id":"67197","score":-0.48735167237049154}
  ],
  "user": "test2",
  "session": "test2",
  "timestamp": 1661431888711
}'
```

> Now, let's intereact with the items `93363`

```bash
# click on the item with id 93363
curl http://localhost:8080/feedback \
 -d '{
  "event": "interaction",
  "type": "click",
  "fields": [],
  "id": "test-interaction",
  "ranking": "test-ranking",
  "item": "93363",
  "user": "test",
  "session": "test",
  "timestamp": 1661431890711
}'
```

> Now, Metarank will personalize the items, the order of the items in the response will be different

```bash
# personalize the same list of items
# they will be returned in a different order by Metarank
curl http://localhost:8080/rank/xgboost \
 -d '{
  "event": "ranking",
  "fields": [],
  "id": "test-personalized",
  "items": [
    {"id":"72998"}, {"id":"67197"}, {"id":"77561"},
    {"id":"68358"}, {"id":"79132"}, {"id":"103228"}, 
    {"id":"72378"}, {"id":"85131"}, {"id":"94864"}, 
    {"id":"68791"}, {"id":"93363"}, {"id":"112623"}
  ],
  "user": "test",
  "session": "test",
  "timestamp": 1661431892711
}'

# {"items":[{"item":"93363","score":2.2013986484185124},{"item":"72998","score":1.1542776301073876},{"item":"68358","score":0.9828904282341605},{"item":"112623","score":0.9521647429731446},{"item":"79132","score":0.9258841742518286},{"item":"77561","score":0.8990921381835769},{"item":"103228","score":0.8990921381835769},{"item":"94864","score":0.7131600718467729},{"item":"68791","score":0.624462038351694},{"item":"72378","score":0.5269765094008626},{"item":"85131","score":0.29198666089255343},{"item":"67197","score":0.16412780810560743}]}
```

## Useful Links

* [Documentation](https://docs.metarank.ai)
* [Ranklens Dataset](https://github.com/metarank/ranklens)
* [Contribution guide](CONTRIBUTING.md)
* [License](LICENSE)

## What's next? 

Check out a more in-depth [Quickstart](/doc/quickstart/quickstart.md) full [Reference](/doc/installation.md). 

If you have any questions, don't hesitate to join our [Slack](https://communityinviter.com/apps/metarank/metarank)!


License
=====
This project is released under the Apache 2.0 license, as specified in the [License](LICENSE) file.
