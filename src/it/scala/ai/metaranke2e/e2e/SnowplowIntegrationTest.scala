package ai.metaranke2e.e2e

import ai.metarank.source.format.SnowplowFormat.SnowplowTSVFormat
import ai.metaranke2e.SnowplowJavaTracker
import ai.metaranke2e.tags.SnowplowTag
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider}
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.regions.Region
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.services.kinesis.KinesisClient
import org.apache.flink.kinesis.shaded.software.amazon.awssdk.services.kinesis.model.{
  GetRecordsRequest,
  GetShardIteratorRequest,
  ShardIteratorType
}
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.jdk.CollectionConverters._
import java.net.URI

@SnowplowTag
class SnowplowIntegrationTest extends AnyFlatSpec with Matchers with Eventually {
  override implicit val patienceConfig = PatienceConfig(
    timeout = scaled(Span(180, Seconds)),
    interval = scaled(Span(1, Seconds))
  )

  def withKinesisClient[T](code: KinesisClient => T) = {
    val client = KinesisClient
      .builder()
      .endpointOverride(new URI("http://localhost:4566"))
      .credentialsProvider(new AwsCredentialsProvider {
        override def resolveCredentials(): AwsCredentials = new AwsCredentials {
          override def accessKeyId(): String     = "default"
          override def secretAccessKey(): String = "default"
        }
      })
      .region(Region.US_EAST_1)
      .build()

    val result = code(client)
    client.close()
    result
  }

  def pollTopic(topic: String, client: KinesisClient): List[Array[Byte]] = {
    val iterator = client.getShardIterator(
      GetShardIteratorRequest
        .builder()
        .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
        .shardId("shardId-000000000000")
        .streamName(topic)
        .build()
    )
    val records = client.getRecords(GetRecordsRequest.builder().shardIterator(iterator.shardIterator()).build())
    records.records().asScala.map(r => r.data().asByteArray()).toList
  }

  it should "send tracked event" in {
    SnowplowJavaTracker.track("http://localhost:8082")
  }

  it should "receive event from good stream" in withKinesisClient { client =>
    eventually {
      val events = pollTopic("good", client)
      events shouldNot be(empty)
    }
  }

  it should "receive events from enrich stream" in withKinesisClient { client =>
    eventually {
      val events = pollTopic("enrich", client)
      val parsed = events.flatMap(e =>
        SnowplowTSVFormat.parse(e) match {
          case Right(Some(value)) => Some(value)
          case _                  => None
        }
      )
      parsed shouldNot be(empty)
    }
  }
}
