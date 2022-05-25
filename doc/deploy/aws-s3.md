# Metarank and AWS S3

Metarank can work with the data stored in AWS S3 (or S3-like data storage) with existing Apache Flink integration. 

To set it up, define the following environment variables:
* `AWS_ACCESS_KEY_ID` - key id
* `AWS_SECRET_ACCESS_KEY` - key secret
* `AWS_S3_ENDPOINT_URL` - optional, needed for non-AWS S3 implementations

Required IAM permissions for the integration:
* `DeleteObject`
* `GetObject`
* `ListBucket`
* `PutObject`
* `ListMultipartUploadParts`
* `AbortMultipartUpload`
* `ListBucketMultipartUploads`