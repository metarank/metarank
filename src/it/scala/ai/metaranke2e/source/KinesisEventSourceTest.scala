package ai.metaranke2e.source

import ai.metarank.config.InputConfig.{KinesisInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KinesisSource
import ai.metarank.util.{FlinkTest, TestItemEvent}
import cats.effect.unsafe.implicits.global
import org.apache.flink.kinesis.shaded.com.amazonaws.services.kinesis.producer.{
  KinesisProducer,
  KinesisProducerConfiguration,
  UserRecord
}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.apache.flink.kinesis.shaded.com.amazonaws.auth.{
  AWSCredentials,
  AWSCredentialsProvider,
  AnonymousAWSCredentials,
  BasicAWSCredentials
}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class KinesisEventSourceTest extends AnyFlatSpec with Matchers {
  it should "read from kinesis" in {
    val conf = KinesisInputConfig(
      topic = "events",
      offset = SourceOffset.Earliest,
      region = "us-east-1",
      endpoint = Some("https://localhost:4567")
    )
    val creds = new AWSCredentialsProvider {
      override def getCredentials: AWSCredentials = new BasicAWSCredentials("1", "1")
      override def refresh(): Unit                = {}
    }
    val prodConf =
      new KinesisProducerConfiguration()
        .setKinesisEndpoint("localhost")
        .setKinesisPort(4567)
        .setRegion("us-east-1")
        .setVerifyCertificate(false)
        .setCredentialsProvider(creds)

    val producer     = new KinesisProducer(prodConf)
    val event: Event = TestItemEvent("p1")
    val eventRecord =
      new UserRecord("events", "ye", ByteBuffer.wrap(event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)))
    val response = producer.addUserRecord(eventRecord).get()
    producer.flushSync()
    producer.destroy()
    val received = KinesisSource(conf).stream.take(1).compile.toList.unsafeRunSync()
    received shouldBe List(event)
  }
}
