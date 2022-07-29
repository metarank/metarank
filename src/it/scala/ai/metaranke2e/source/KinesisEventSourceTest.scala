package ai.metaranke2e.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KinesisSource
import ai.metarank.source.KinesisSource.Consumer
import ai.metarank.util.TestItemEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import java.nio.charset.StandardCharsets

class KinesisEventSourceTest extends AnyFlatSpec with Matchers {
  it should "read from kinesis" in {
    val conf = KinesisInputConfig(
      topic = "events",
      offset = SourceOffset.Earliest,
      region = "us-east-1",
      endpoint = Some("https://localhost:4567"),
      skipCertVerification = true
    )
    val client       = Consumer.create(conf).unsafeRunSync().client
    val event: Event = TestItemEvent("p1")
    val response = client
      .putRecord(
        PutRecordRequest
          .builder()
          .streamName(conf.topic)
          .partitionKey("whatever")
          .data(SdkBytes.fromByteArray(event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)))
          .build()
      )
      .get()
    client.close()
    val received = KinesisSource(conf).stream.take(1).compile.toList.unsafeRunSync()
    received shouldBe List(event)
  }
}
