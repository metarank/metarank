package ai.metaranke2e.e2e

import ai.metarank.config.SourceFormat.SnowplowTSV
import ai.metaranke2e.SnowplowJavaTracker
import better.files.Resource
import cats.data.Validated
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.regions.Region
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.services.kinesis.KinesisClient
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.services.kinesis.model.{GetRecordsRequest, GetShardIteratorRequest, ShardIteratorType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._
import java.net.URI

class SnowplowIntegrationTest extends AnyFlatSpec with Matchers {
  it should "emit events into the collector " in {
    SnowplowJavaTracker.track()
    val client = KinesisClient
      .builder()
      .endpointOverride(new URI("http://localhost:4566"))
      .region(Region.US_EAST_1)
      .build()

    val iterator = client.getShardIterator(
      GetShardIteratorRequest
        .builder()
        .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
        .shardId("shardId-000000000000")
        .streamName("enriched")
        .build()
    )

    val records = client.getRecords(GetRecordsRequest.builder().shardIterator(iterator.shardIterator()).build())
    val events  = records.records().asScala.map(r => new String(r.data().asByteArray())).toList
    val parsed = events.map(SnowplowTSV.parse).collect { case Validated.Valid(a) =>
      a
    }

  }
}
