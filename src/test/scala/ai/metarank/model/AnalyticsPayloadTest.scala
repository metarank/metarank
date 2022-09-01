package ai.metarank.model

import ai.metarank.main.CliArgs.TrainArgs
import ai.metarank.model.AnalyticsPayload.UsedFeature
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.TestConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import java.nio.file.Path

class AnalyticsPayloadTest extends AnyFlatSpec with Matchers {
  it should "generate payload" in {
    val payload = AnalyticsPayload(TestConfig(), TrainArgs(Path.of("x"), "foo"))
    payload.state shouldBe "memory"
    payload.modelTypes shouldBe List("shuffle")
    payload.usedFeatures shouldBe List(UsedFeature(FeatureName("price"), "number"))
    val json = payload.asJson.spaces2
    val br   = 1
  }
}
