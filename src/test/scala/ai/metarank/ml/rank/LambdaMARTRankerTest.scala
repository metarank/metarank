package ai.metarank.ml.rank

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config.WarmupConfig
import ai.metarank.ml.Predictor.EmptyDatasetException
import ai.metarank.ml.PredictorSuite
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.FeatureWeight.SingularWeight
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.{TestClickthroughValues, TestQueryRequest}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import ai.metarank.main.command.train.SplitStrategy.Split
import io.github.metarank.ltrlib.booster.XGBoostBooster
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, LabeledItem, Query}
import io.github.metarank.ltrlib.model.Feature.{CategoryFeature, SingularFeature, VectorFeature}

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.*
class LambdaMARTRankerTest extends PredictorSuite[LambdaMARTConfig, QueryRequest, LambdaMARTModel] {
  val conf = LambdaMARTConfig(
    backend = XGBoostConfig(),
    features = NonEmptyList.of(FeatureName("foo")),
    weights = Map("click" -> 1.0)
  )
  val desc = DatasetDescriptor(List(SingularFeature("foo")))

  override def cts: List[ClickthroughValues] =
    (0 until 100).map(_ => TestClickthroughValues.random(List("p1", "p2", "p3"))).toList

  override def predictor = LambdaMARTPredictor("foo", conf, desc)

  override def request(n: Int): QueryRequest = TestQueryRequest(n)

  it should "fail on ct with no mvalues" in {
    val err = Try(predictor.fit(fs2.Stream(cts.map(_.copy(values = Nil))*)).unsafeRunSync())
    err should matchPattern { case Failure(ex: EmptyDatasetException) => // yep
    }
  }

  it should "fail when dataset is too large" in {
    val result = Try(
      LambdaMARTRanker
        .checkDatasetSize(
          itemCount = 3000000,
          dim = 1000,
          groupsCount = 30000,
          List(SingularFeature("foo"), VectorFeature("bar", 999))
        )
        .unsafeRunSync()
    )
    result shouldBe a[Failure[?]]
  }

  it should "fail roundtrip the model on feature mismatch" in {
    val conf = LambdaMARTConfig(
      backend = XGBoostConfig(),
      features = NonEmptyList.of(FeatureName("bar")),
      weights = Map("click" -> 1.0)
    )
    val desc   = DatasetDescriptor(List(SingularFeature("bar")))
    val pred2  = LambdaMARTPredictor("foo", conf, desc)
    val model  = predictor.fit(fs2.Stream(cts*)).unsafeRunSync()
    val blob   = model.save()
    val result = Try(pred2.load(blob).unsafeRunSync())
    result.isSuccess shouldBe false
  }

  it should "roundtrip with warmup requests" in {
    val conf = LambdaMARTConfig(
      backend = XGBoostConfig(),
      features = NonEmptyList.of(FeatureName("foo")),
      weights = Map("click" -> 1.0),
      warmup = Some(WarmupConfig(sampledRequests = 10, duration = 1.second))
    )
    val desc   = DatasetDescriptor(List(SingularFeature("bar")))
    val pred2  = LambdaMARTPredictor("foo", conf, desc)
    val model  = pred2.fit(fs2.Stream(cts*)).unsafeRunSync()
    val blob   = model.save()
    val result = Try(pred2.load(blob).unsafeRunSync())
    result.map(_.warmupRequests.size) shouldBe Success(10)
  }

  val catDesc = DatasetDescriptor(List(CategoryFeature("cat"), SingularFeature("num")))
  val catDataset = {
    val random = new scala.util.Random(0)
    val groups = (0 until 200).map(group => {
      val items = (0 until 10).map(_ => {
        val cat   = random.nextInt(20)
        val label = if (Set(3, 11, 17).contains(cat)) 1.0 else 0.0
        LabeledItem(label, group, Array(cat.toDouble, random.nextDouble()))
      })
      Query(catDesc, items.toList)
    })
    Dataset(catDesc, groups.toList)
  }

  def fitCategorical(treeMethod: String): LambdaMARTModel = {
    val conf = LambdaMARTConfig(
      backend = XGBoostConfig(iterations = 10, treeMethod = treeMethod),
      features = NonEmptyList.of(FeatureName("cat"), FeatureName("num")),
      weights = Map("click" -> 1.0)
    )
    val booster = LambdaMARTPredictor("foo", conf, catDesc).makeBooster(Split(catDataset, catDataset))
    LambdaMARTModel("foo", conf, booster, Nil)
  }

  def categoricalSplits(model: LambdaMARTModel): Boolean = {
    val json = new String(model.booster.save().drop(1).dropWhile(_ != '{'.toByte))
    "\"categories_nodes\":\\[\\d".r.findFirstIn(json).isDefined
  }

  it should "make categorical splits with hist and roundtrip them" in {
    val model = fitCategorical("hist")
    categoricalSplits(model) shouldBe true
    val pred   = LambdaMARTPredictor("foo", model.conf, catDesc)
    val loaded = pred.load(model.save()).unsafeRunSync()
    loaded.booster shouldBe a[XGBoostBooster]
    val row = Array(3.0, 0.5, 4.0, 0.5)
    loaded.booster.predictMat(row, 2, 2).toList shouldBe model.booster.predictMat(row, 2, 2).toList
  }

  it should "make no categorical splits with exact and roundtrip the model" in {
    val model = fitCategorical("exact")
    categoricalSplits(model) shouldBe false
    val pred   = LambdaMARTPredictor("foo", model.conf, catDesc)
    val loaded = pred.load(model.save()).unsafeRunSync()
    val row    = Array(3.0, 0.5, 4.0, 0.5)
    loaded.booster.predictMat(row, 2, 2).toList shouldBe model.booster.predictMat(row, 2, 2).toList
  }

  it should "report weights of categorical splits" in {
    val weights = fitCategorical("hist").weights(catDesc)
    weights("cat") should matchPattern { case SingularWeight(w) if w > 0 => }
  }

  it should "report the booster's xgboost weights without categorical features" in {
    val numDesc = DatasetDescriptor(List(SingularFeature("cat"), SingularFeature("num")))
    val numData = Dataset(numDesc, catDataset.groups)
    val conf = LambdaMARTConfig(
      backend = XGBoostConfig(iterations = 10),
      features = NonEmptyList.of(FeatureName("cat"), FeatureName("num")),
      weights = Map("click" -> 1.0)
    )
    val booster = LambdaMARTPredictor("foo", conf, numDesc).makeBooster(Split(numData, numData))
    val w       = booster.weights()
    w.sum should be > 0.0
    LambdaMARTModel("foo", conf, booster, Nil).weights(numDesc) shouldBe
      Map("cat" -> SingularWeight(w(0)), "num" -> SingularWeight(w(1)))
  }
}
