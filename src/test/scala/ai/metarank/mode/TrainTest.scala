package ai.metarank.mode

import ai.metarank.mode.train.Train
import ai.metarank.mode.train.TrainCmdline.{LambdaMARTLightGBM, LambdaMARTXGBoost}
import better.files.File
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.model.Feature.SingularFeature
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Query}
import org.scalacheck.Gen
import org.scalatest.Ignore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{Checkers, ScalaCheckPropertyChecks}

import scala.util.Try

class TrainTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks with Checkers {
  val ROWS                                    = 10
  val COLS                                    = 10
  implicit override val generatorDrivenConfig = PropertyCheckConfiguration(minSuccessful = 10)

  def randomArray(n: Int): Gen[Array[Double]] = for {
    nums <- Gen.listOfN(n, Gen.chooseNum(0.0, 1.0))
  } yield {
    nums.toArray
  }
  implicit val queryGen: Gen[Query] = for {
    group  <- Gen.chooseNum[Int](1, 1000000)
    labels <- Gen.listOfN(ROWS, Gen.oneOf(0.0, 1.0)).map(_.toArray)
    values <- Gen.listOfN(ROWS * COLS, Gen.chooseNum(0.0, 1.0)).map(_.toArray)
  } yield {
    Query(
      group = group,
      labels = labels,
      values = values,
      rows = ROWS,
      columns = COLS
    )
  }

  val desc = DatasetDescriptor((0 until 10).map(i => SingularFeature(s"f$i")).toList)

  // dies in LGBM, see https://github.com/metarank/metarank/issues/338
  it should "train the model" ignore {
    forAll(Gen.listOfN(1000, queryGen), Gen.oneOf(LambdaMARTLightGBM, LambdaMARTXGBoost)) { (queries, booster) =>
      {
        val (train, test) = Train.split(Dataset(desc, queries), 80)
        Train.trainModel(train, test, booster, 10)
      }
    }
  }

  it should "fail on empty dir" in {
    Try(Train.loadData(File("/no/such/dir"), desc).unsafeRunSync()).isFailure shouldBe true
  }
}
