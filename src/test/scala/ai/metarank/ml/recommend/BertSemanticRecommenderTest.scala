package ai.metarank.ml.recommend

import ai.metarank.config.ModelConfig
import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.ml.onnx.{EmbeddingCache, ModelHandle}
import ai.metarank.ml.onnx.encoder.Encoder
import ai.metarank.ml.onnx.encoder.EncoderType.BertEncoderType
import ai.metarank.ml.recommend.BertSemanticRecommender.{BertSemanticModelConfig, BertSemanticPredictor}
import ai.metarank.ml.recommend.KnnConfig.HnswConfig
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.TrainValues
import ai.metarank.model.TrainValues.ItemValues
import ai.metarank.util.{CSVStream, RanklensEvents}
import better.files.Resource
import cats.effect.unsafe.implicits.global
import com.opencsv.CSVReader
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets

class BertSemanticRecommenderTest extends AnyFlatSpec with Matchers {
  it should "train the model" in {
    val conf = BertSemanticModelConfig(
      encoder = BertEncoderType(ModelHandle("metarank", "all-MiniLM-L6-v2"), dim = 384),
      itemFields = List("title", "description"),
      store = HnswConfig()
    )
    val model                    = BertSemanticPredictor("foo", conf)
    val events: List[ItemValues] = RanklensEvents.apply().collect { case e: ItemEvent => ItemValues(e) }
    val p                        = model.fit(fs2.Stream(events.take(500): _*)).unsafeRunSync()
  }

  it should "decode semantic config" in {
    val yaml =
      """type: semantic
        |encoder:
        |  type: transformer
        |  model: metarank/all-MiniLM-L6-v2
        |  dim: 384
        |itemFields: [title, description]""".stripMargin
    val decoded = io.circe.yaml.parser.parse(yaml).flatMap(_.as[ModelConfig])
    decoded shouldBe Right(
      BertSemanticModelConfig(
        encoder = BertEncoderType(ModelHandle("metarank", "all-MiniLM-L6-v2"), dim = 384),
        itemFields = List("title", "description"),
        store = HnswConfig()
      )
    )
  }

  it should "load cohere embeddings" in {
    val lines   = Resource.my.getAsStream("/embedding/cohere.csv")
    val encoder = EmbeddingCache.fromStream(CSVStream.fromStream(lines, ',', 0), 4096).unsafeRunSync()
    encoder.get("8").map(_.length) shouldBe Some(4096)
  }

}

object BertSemanticRecommenderTest {
  case class Movie(id: ItemId, title: String, desc: String)
}
