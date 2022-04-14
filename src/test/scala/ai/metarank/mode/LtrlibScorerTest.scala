package ai.metarank.mode

import ai.metarank.mode.inference.ranking.LtrlibScorer
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.booster.{LightGBMBooster, XGBoostBooster}
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LtrlibScorerTest extends AnyFlatSpec with Matchers {
  it should "load xgboost" in {
    val model = LtrlibScorer.fromBytes(IOUtils.resourceToByteArray("/models/xgboost.model")).unsafeRunSync()
    model.booster shouldBe a[XGBoostBooster]
  }

  it should "load base64 xgboost" in {
    val model = LtrlibScorer.fromBytes(IOUtils.resourceToByteArray("/models/xgboost-b64.model")).unsafeRunSync()
    model.booster shouldBe a[XGBoostBooster]
  }

  it should "load lightgbm" in {
    val model = LtrlibScorer.fromBytes(IOUtils.resourceToByteArray("/models/lightgbm.model")).unsafeRunSync()
    model.booster shouldBe a[LightGBMBooster]
  }
}
