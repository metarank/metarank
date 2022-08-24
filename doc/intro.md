# What is Metarank?

Metarank is a personalization service that can be easily integrated into existing systems and used to personalize different types of content. 

Like Instagram’s personalized feed that is based on the posts that you’ve seen and liked, Facebook’s new friends recommendation widget or Amazon’s personalized results, you can add personalization to your application. You can combine different features, both user-based like location or gender and item-based like tags with different actions: clicks, likes, purchases to create a personalized experience for your users.

Thanks to Metarank’s simple API and YAML configuration, you don’t need any prio machine learning experience to start improving your key metrics and run experiments.

![Demo](./img/demo.gif)

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

```