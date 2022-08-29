# User session feature extractors

## User-Agent field extractor

A typical HTTP User-Agent field has quite a lot of embedded meta information, which can be useful for ranking:
* is it mobile or desktop? Mobile visitors behave differently compared to desktop ones as they scroll less and
  get distracted quicker. 
* iOS or Android? Assuming that on average Apple devices are more expensive than Android ones, it can also 
  provide more insights on visitor goals.
* Stock browser or something custom?
* How old is the OS? On Android, an ancient version of OS can mean an old and unsupported device, so it can be also
  a signal on your ranking.

But User-Agent string is quite cryptic:
```
Mozilla/5.0 (iPad; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/99.0.4844.47 Mobile/15E148 Safari/604.1
Mozilla/5.0 (Linux; Android 10; LM-Q720) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.48 Mobile Safari/537.36
Mozilla/5.0 (Macintosh; Intel Mac OS X 12_2_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15
Mozilla/5.0 (iPhone; CPU iPhone OS 15_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Mobile/15E148 Safari/604.1
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.51 Safari/537.36 Edg/98.0.1108.62
```

There is a large collaborative effort to build a database of typical UA patterns, (UA-Parser)[https://github.com/ua-parser],
which is used to extract all the possible item metadata from these strings. 

To map this to actual ML features, there is a predefined set of mappers:
* platform: mobile, desktop, tablet
* os: ios, android, windows, linux, macos, chrome os
* browser: safari, chrome, firefox, opera, ie, other
* bot: is it a known crawler or not

To configure the extractor, use this YAML snippet:
```yaml

- name: platform_feature // just a name of this feature
  type: ua
  
  # take the UA field from ranking event
  source: "ranking.ua"
  
  # options: platform, os, browser, bot
  field: "platform"
  
  # optional, how frequently we should update the value
  refresh: 0s

  # optional, how long should we remember this field
  ttl: 90d
```

The UA field is taken from each ranking request, so it should be always present.

## Interacted with

For the current item, did this visitor have an interaction with other item with the same field.

Example:
```yaml
- name: clicked_color
  type: interacted_with
  # type of the interaction event (interaction.type field)
  interaction: click
  field: item.color // must be a string or string[], and only works with item fields

  # session/user
  scope: user
```

For this example, Metarank will track all color field values for all items visitor clicked and intersect this set 
with per-item field values in the ranking.

## Referer

For user/ranking/interaction events it's possible to parse a HTTP Referer field and extract the source medium.
We use a [snowplow referer parser](https://s3-eu-west-1.amazonaws.com/snowplow-hosted-assets/third-party/referer-parser/referers-latest.json)
parsing library, so it defines 6 types of referer mediums: unknown, search, internal, social, email, paid.

For a ranking event:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "referer", "value": "http://www.google.com"}
  ],
  "items": [
    {"id": "item1", "relevancy":  1.0},
    {"id": "item2", "relevancy":  0.5} 
  ]
}
```

and a configuration:
```yaml
- name: referer_medium
  type: referer
  source: ranking.referer
  scope: user // can be user/session
```

It will detect that it's a "search" medium and one-hot-encode it to `[0, 1, 0, 0, 0, 0]`.

A source field can be of a user/ranking/interaction type, and feature extractor memorises all the referer fields ingested:
* it matches the HTTP Referer semantics, as referer field is sent on each request
* there can be multiple referers. For example, visitor lands on a site from google (and gets a "search" referer),
  then does a couple of interactions with the site (and also gets an "internal" referer medium)

In a case when a visitor has multiple referers memorized, then the one-hot-encoded vector will have multiple flags enabled,
like `[0, 1, 1, 0, 0, 0]` for a case with search+internal referer mediums.