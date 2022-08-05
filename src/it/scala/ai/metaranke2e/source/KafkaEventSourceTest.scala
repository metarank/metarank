package ai.metaranke2e.source

import ai.metarank.config.InputConfig.{KafkaInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KafkaSource
import ai.metarank.util.TestItemEvent
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.circe.syntax._
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.{Collections, Properties}
import scala.util.Random

class KafkaEventSourceTest extends AnyFlatSpec with Matchers {

  implicit val serializer: Serializer[String] = new StringSerializer()
  val event: Event                            = TestItemEvent("p1")
  val topic                                   = s"topic${Random.nextInt(10240000)}"

  it should "receive events from kafka" in {

    val sourceConfig = KafkaInputConfig(
      brokers = NonEmptyList.of(s"localhost:9092"),
      topic = topic,
      groupId = "metarank",
      offset = Some(SourceOffset.Earliest)
    )
    val props = new Properties()
    props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    val admin = Admin.create(props)
    admin.createTopics(Collections.singleton(new NewTopic(topic, 1, 1.toShort)))
    val producer = new KafkaProducer[String, String](props, serializer, serializer)
    val record   = new ProducerRecord[String, String](topic, null, event.asJson.noSpaces)
    producer.send(record).get()
    producer.flush()
    Thread.sleep(1000)
    val recieved = KafkaSource(sourceConfig).stream.take(1).compile.toList.unsafeRunSync()
    recieved shouldBe List(event)

  }

}
