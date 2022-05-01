package ai.metarank.source

import org.apache.pulsar.PulsarStandalone
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PulsarEventSourceTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  var server: PulsarStandalone = _
  override def beforeAll = {
    server = PulsarStandalone.builder().withOnlyBroker(true).build()
    server.start()
  }

  override def afterAll(): Unit = {
    server.close()
  }

  it should "receive events from pulsar" in {
    //
  }
}
