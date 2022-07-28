package ai.metaranke2e.source

import ai.metarank.config.InputConfig.{KafkaInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.KafkaSource
import ai.metarank.util.{FlinkTest, TestItemEvent}
import cats.data.NonEmptyList
import io.circe.syntax._
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.{Collections, Properties}

class KafkaEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._

  implicit val serializer: Serializer[String] = new StringSerializer()
  val event: Event                            = TestItemEvent("p1")

  it should "receive events from kafka" in {

    val sourceConfig = KafkaInputConfig(
      brokers = NonEmptyList.of(s"localhost:9092"),
      topic = "events",
      groupId = "metarank",
      offset = SourceOffset.Earliest
    )
    val props = new Properties()
    props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    val admin = Admin.create(props)
    admin.createTopics(Collections.singleton(new NewTopic("events", 1, 1.toShort)))
    val producer = new KafkaProducer[String, String](props, serializer, serializer)
    val record   = new ProducerRecord[String, String]("events", null, event.asJson.noSpaces)
    producer.send(record).get()
    producer.flush()
    Thread.sleep(1000)
//    val recieved = KafkaSource(sourceConfig).eventStream(env, true).executeAndCollect(1)
//    recieved shouldBe List(event)

  }

}
