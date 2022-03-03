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
which is used to extract all the possible metadata from these strings. 

To map this to actual ML features, there is a predefined set of mappers:
* platform: mobile, desktop, tablet
* os: ios, android, windows, linux, macos, chrome os

To configure the extractor, use this YAML snippet:
```yaml
  // just a name of this feature
- name: "platform_feature"
  
  // take the UA field from ranking event
  // can also be an interaction and depends on your data model
  source: "ranking.ua"
  
  // options: platform, os
  field: "platform"
  
  // technically, parsed user agent fields are stored in a feature store,
  // so you can send this field only once, and it will be retrieved from
  // the store automatically. Scope is a key used to store the value.
  // Good options are: session/user
  scope: session

  // optional, how frequently we should update the value
  refresh: 0s

  // optional, how long should we remember this field
  ttl: 90d
```

## Interacted with

For the current item, did this visitor have an interaction with other item with the same field.

Example:
```yaml
- name: clicked_color
  type: interacted_with
  // type of the interaction event (interaction.type field)
  interaction: click
  field: metadata.color

  // session/user
  scope: user
```

For this example, Metarank will track all color field values for all items visitor clicked and intersect this set with per-item field values in the ranking.