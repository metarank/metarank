package ai.metarank.ml.onnx.distance

import ai.metarank.ml.onnx.distance.DistanceFunction.{CosineDistance, DotDistance}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DistanceFunctionTest extends AnyFlatSpec with Matchers {
  it should "compute cosine distance" in {
    CosineDistance.dist(Array(1.0f, 0.0f), Array(1.0, 0.0)) shouldBe 1.0 +- 0.001
    CosineDistance.dist(Array(1.0f, 0.0f), Array(0.0, 1.0)) shouldBe 0.0 +- 0.001
    CosineDistance.dist(Array(1.0f, 2.0f), Array(2.0, 4.0)) shouldBe 1.0 +- 0.001
  }

  it should "compute dot distance" in {
    DotDistance.dist(Array(1.0f, 2.0f), Array(3.0, 4.0)) shouldBe 11.0 +- 0.001
    DotDistance.dist(Array(1.0f, 0.0f), Array(0.0, 1.0)) shouldBe 0.0 +- 0.001
  }
}
