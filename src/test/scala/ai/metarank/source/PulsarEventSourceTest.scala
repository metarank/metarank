package ai.metarank.source

import ai.metarank.config.EventSourceConfig.{KafkaSourceConfig, PulsarSourceConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.util.{FlinkTest, TestItemEvent}
import io.circe.syntax._
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.common.policies.data.{ClusterData, TenantInfo}
import org.scalatest.Ignore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import java.nio.charset.StandardCharsets
import java.util.Collections

@Ignore
class PulsarEventSourceTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._

  it should "receive events from pulsar" in {
    val sourceConfig = PulsarSourceConfig(
      serviceUrl = "pulsar://localhost:6650",
      adminUrl = "http://localhost:8080",
      topic = "persistent://tenant/namespace/events",
      subscriptionName = "metarank",
      subscriptionType = "shared",
      offset = SourceOffset.Earliest
    )
    val admin = PulsarAdmin.builder().serviceHttpUrl(sourceConfig.adminUrl).build()
    admin.tenants().createTenant("tenant", TenantInfo.builder().allowedClusters(Set("standalone").asJava).build())
    admin.namespaces().createNamespace("tenant/namespace")
    admin.topics().createNonPartitionedTopic("events")
    val client       = PulsarClient.builder().serviceUrl(sourceConfig.serviceUrl).build()
    val producer     = client.newProducer().topic(sourceConfig.topic).create()
    val event: Event = TestItemEvent("p1")
    producer.send(event.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    producer.flush()
    val received = PulsarEventSource(sourceConfig).eventStream(env, true).executeAndCollect(1)
    received shouldBe List(event)
  }
}
