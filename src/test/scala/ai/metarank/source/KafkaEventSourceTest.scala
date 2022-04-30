package ai.metarank.source

import ai.metarank.config.EventSourceConfig.{KafkaSourceConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.util.{FlinkTest, TestItemEvent}
import cats.data.NonEmptyList
import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import scala.util.Random

class KafkaEventSourceTest extends AnyFlatSpec with Matchers with EmbeddedKafka with BeforeAndAfterAll with FlinkTest {
  import ai.metarank.mode.TypeInfos._
  val port                       = 6000 + Random.nextInt(30000)
  implicit val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = port, zooKeeperPort = 0)

  implicit val serializer: Serializer[String] = new StringSerializer()
  val event: Event                            = TestItemEvent("p1")

  it should "receive events from kafka" in {
    withRunningKafka {
      {
        val sourceConfig = KafkaSourceConfig(
          brokers = NonEmptyList.of(s"localhost:$port"),
          topic = "events",
          groupId = "metarank",
          offset = SourceOffset.Earliest
        )
        createCustomTopic("events")
        publishToKafka("events", event.asJson.noSpaces)
        val recieved = KafkaSource(sourceConfig).eventStream(env, true).executeAndCollect(10)
        recieved shouldBe List(event)
      }
    }
  }

}
