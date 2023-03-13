package ai.metarank.ml.recommend

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.ml.recommend.BertSemanticRecommender.{BertSemanticModelConfig, BertSemanticPredictor}
import ai.metarank.ml.recommend.BertSemanticRecommender.EncoderType.BertEncoderType
import ai.metarank.ml.recommend.BertSemanticRecommenderTest.Movie
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.TrainValues
import ai.metarank.model.TrainValues.ItemValues
import ai.metarank.util.RanklensEvents
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BertSemanticRecommenderTest extends AnyFlatSpec with Matchers {
  it should "train the model" in {
    val conf = BertSemanticModelConfig(
      encoder = BertEncoderType("sentence-transformer/all-MiniLM-L6-v2"),
      itemFields = List("title", "description")
    )
    val model                    = BertSemanticPredictor("foo", conf)
    val events: List[ItemValues] = RanklensEvents.apply().collect { case e: ItemEvent => ItemValues(e) }
    val p                        = model.fit(fs2.Stream(events.take(500): _*)).unsafeRunSync()
  }
}

object BertSemanticRecommenderTest {
  case class Movie(id: ItemId, title: String, desc: String)
}
