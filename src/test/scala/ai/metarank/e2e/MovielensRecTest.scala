package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.api.routes.RankApi.RankResponse
import ai.metarank.config.Config
import ai.metarank.flow.TrainBuffer
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.main.command.{Import, Train}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTPredictor}
import ai.metarank.util.RanklensEvents
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ai.metarank.fstore.memory.{MemTrainStore, MemPersistence}

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.ml.Recommender
import ai.metarank.ml.recommend.MFRecommender.MFPredictor
import ai.metarank.ml.recommend.RecommendRequest
import ai.metarank.ml.recommend.TrendingRecommender.TrendingPredictor
import ai.metarank.model.Event
import ai.metarank.model.Identifier.ItemId
import ai.metarank.tool.MovielensRatingsSource
import better.files.Resource
import cats.effect.IO

import java.util.zip.GZIPInputStream

class MovielensRecTest extends AnyFlatSpec with Matchers {
  val config = Config
    .load(IOUtils.resourceToString("/movielens/config.yml", StandardCharsets.UTF_8), Map.empty)
    .unsafeRunSync()
  val mapping    = FeatureMapping.fromFeatureSchema(config.features, config.models)
  lazy val store = MemPersistence(mapping.schema)
  lazy val cts   = MemTrainStore()

  val similar  = mapping.models("similar").asInstanceOf[MFPredictor]
  val trending = mapping.models("trending").asInstanceOf[TrendingPredictor]

  lazy val buffer = TrainBuffer(ClickthroughJoinConfig(), store.values, cts, mapping)
  lazy val stream = new GZIPInputStream(Resource.my.getAsStream("/movielens/ratings.dat.gz"))
  lazy val rec    = Recommender(mapping, store)

  it should "import events" in {
    val blob =
      MovielensRatingsSource.fromInputStream(stream).take(100000).compile.toList.unsafeRunSync().sortBy(_.timestamp.ts)
    Import.slurp(fs2.Stream[IO, Event](blob: _*), store, mapping, buffer, config).unsafeRunSync()
    buffer.flushAll().unsafeRunSync()
  }

  it should "train the similar model" in {
    Train.train(store, cts, similar).unsafeRunSync()
  }

  it should "train the trending model" in {
    Train.train(store, cts, trending).unsafeRunSync()
  }

  it should "recommend things" in {
    val req      = RecommendRequest(10, items = List(ItemId("661")))
    val response = rec.recommend(req, "similar").unsafeRunSync()
    response.items.size shouldBe 10
  }

  it should "show trending" in {
    val req      = RecommendRequest(10)
    val response = rec.recommend(req, "trending").unsafeRunSync()
    response.items.size shouldBe 10
  }

}
