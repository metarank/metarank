## Request

the same as impression event, see [API Schema](xx_api_schema.md) for details:
```json
{
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",// required
  "timestamp": "1599391467000",// required
  "user": "user1",// required
  "session": "session1",// required
  "fields": [
    {"name": "query", "value": "jeans"},
    {"name": "source", "value": "search"}
  ],
  "items": [
    {"id": "product3", "relevancy":  2.0},
    {"id": "product1", "relevancy":  1.0},
    {"id": "product2", "relevancy":  0.5}
  ]
}
```

## Response
```json
{
  "items": [
    {"id": "product3", "relevancy":  2.0},
    {"id": "product1", "relevancy":  1.0},
    {"id": "product2", "relevancy":  0.5}
  ]
}
```