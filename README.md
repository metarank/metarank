<h1 align="center">
    <a style="text-decoration: none" href="https://www.metarank.ai">
      <img width="120" src="https://raw.githubusercontent.com/metarank/metarank/master/doc/img/logo.svg" />
      <p align="center">Metarank: real time personalization as a service</p>
    </a>
</h1>
<h2 align="center">
  <a href="https://docs.metarank.ai">Docs</a> | <a href="https://metarank.ai">Website</a> | <a href="https://metarank.ai/slack">Community Slack</a> | <a href="https://medium.com/metarank">Blog</a> | <a href="https://demo.metarank.ai">Demo</a>
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

### Step 1: Get Metarank

```bash
docker pull metarank:latest
```

### Step 2: Prepare data

We will use the [ranklens dataset](https://github.com/metarank/ranklens), which is used in our [Demo](https://demo.metarank.ai), so just download the data file

```bash
wget https://github.com/metarank/metarank/raw/master/src/test/resources/ranklens/events/events.jsonl.gz
```

### Step 3: Prepare configuration file

We will again use the configuration file from our [Demo](https://demo.metarank.ai). It utilizes in-memory store, so no other dependencies are needed.


```bash
wget https://raw.githubusercontent.com/metarank/metarank/master/src/test/resources/ranklens/config.yml
```

### Step 4: Start Metarank!

With the final step we will use Metarank’s `standalone` mode that combines training and running the API into one command:

```bash
docker run metarank/metarank:latest standalone --config config.yml --data events.jsonl.gz
```

You will see some useful output while Metarank is starting and grinding through the data. Once this is done, you can send requests to `localhost:8080` to get personalized results:

```bash
curl -X POST http://localhost:8080/feedback -d '{
    "event": "ranking",
    "fields": [],
    "id": "test-ranking",
    "items": [{"id": "192389"}, {"id": "95510"}, {"id": "5349"}, {"id": "52722"}, {"id": "110553"}],
    "user": "test2",
    "session": "test2",
    "timestamp": 1661345221008
}'

curl -X POST http://localhost:8080/feedback -d '{
    "event": "interaction",
    "type": "click",
    "fields": [],
    "id": "test-interaction",
    "ranking": "test-ranking",
    "item": "110553",
    "user": "test",
    "session": "test",
    "timestamp": 1661345223008
}'

curl -X POST http://localhost:8080/rank/xgboost -d '{
    "event": "ranking",
    "fields": [],
    "id": "test-personalized",
    "items": [{"id": "192389"}, {"id": "95510"}, {"id": "5349"}, {"id": "52722"}, {"id": "110553"}],
    "user": "test",
    "session": "test",
    "timestamp": 1661345231008
}'
```

## Useful Links

* [Documentation](https://docs.metarank.ai)
* [Ranklens Dataset](https://github.com/metarank/ranklens) that is used in the demo
* [Contribution guide](CONTRIBUTING.md)
* [License](LICENSE)


Licence
=====
This project is released under the Apache 2.0 license, as specified in the [License](LICENSE) file.
