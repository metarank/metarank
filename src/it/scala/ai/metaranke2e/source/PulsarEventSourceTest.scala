package ai.metaranke2e.source

import ai.metarank.config.InputConfig.{PulsarInputConfig, SourceOffset}
import ai.metarank.model.Event
import ai.metarank.source.PulsarEventSource
import ai.metarank.util.TestItemEvent
import cats.effect.unsafe.implicits.global
import io.circe.syntax._
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.common.policies.data.TenantInfo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

class PulsarEventSourceTest extends AnyFlatSpec with Matchers {

  it should "receive events from pulsar" in {
    val sourceConfig = PulsarInputConfig(
      serviceUrl = "pulsar://localhost:6650",
      adminUrl = "http://localhost:8080",
      topic = "persistent://tenant/namespace/events",
      subscriptionName = "metarank",
      subscriptionType = "shared",
      offset = Some(SourceOffset.Earliest)
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
    val received = PulsarEventSource(sourceConfig).stream.take(1).compile.toList.unsafeRunSync()
    received shouldBe List(event)
  }
}
