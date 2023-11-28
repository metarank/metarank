package ai.metarank.ml.recommend

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.onnx.EmbeddingCache
import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.encoder.EncoderConfig
import ai.metarank.ml.onnx.encoder.EncoderConfig.BiEncoderConfig
import ai.metarank.ml.onnx.sbert.{OnnxBiEncoder, OnnxSession}
import ai.metarank.ml.recommend.KnnConfig.HnswConfig
import ai.metarank.ml.recommend.MFRecommender.EmbeddingSimilarityModel
import ai.metarank.ml.recommend.embedding.{EmbeddingMap, HnswJavaIndex, KnnIndex}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{FieldName, TrainValues}
import ai.metarank.model.TrainValues.ItemValues
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure}

object BertSemanticRecommender {
  case class BertSemanticPredictor(name: String, config: BertSemanticModelConfig)
      extends RecommendPredictor[BertSemanticModelConfig, EmbeddingSimilarityModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[EmbeddingSimilarityModel] = for {
      session <- config.encoder.model match {
        case Some(handle) =>
          OnnxSession.load(handle, config.encoder.dim, config.encoder.modelFile, config.encoder.tokenizerFile)
        case None => IO.raiseError(new Exception("you need to define an embedding model"))
      }
      encoder    <- IO { OnnxBiEncoder(session) }
      fieldSet   <- IO(config.itemFields.toSet)
      items      <- data.collect { case item: ItemValues => item }.compile.toList
      _          <- info(s"Loaded ${items.size} items")
      embeddings <- embed(items, fieldSet, encoder)
      index      <- KnnIndex.write(embeddings, config.store)
    } yield {
      EmbeddingSimilarityModel(name, index)
    }

    override def load(bytes: Option[Array[Byte]]): IO[EmbeddingSimilarityModel] = bytes match {
      case Some(value) =>
        KnnIndex.load(value, config.store).map(index => EmbeddingSimilarityModel(name, index))
      case None => IO.raiseError(new Exception(s"cannot load index $name: not found"))
    }

    def embed(items: List[ItemValues], fieldSet: Set[String], encoder: OnnxBiEncoder): IO[EmbeddingMap] = IO {
      val ids        = items.map(_.item.value).toArray
      var j          = 0
      var batchStart = System.currentTimeMillis()
      var batchIndex = 0
      val embeddings = for {
        item <- items.toArray
        stringFields = item.fields.flatMap {
          case StringField(name, value) if fieldSet.contains(name)     => List(value)
          case StringListField(name, value) if fieldSet.contains(name) => value
          case _                                                       => Nil
        }
        floats <- encoder.embed(Array(stringFields.mkString(" ")))
      } yield {
        val doubles = new Array[Double](floats.length)
        var i       = 0
        while (i < doubles.length) {
          doubles(i) = floats(i).toDouble
          i += 1
        }
        j += 1
        if (j % 100 == 0) {
          val now  = System.currentTimeMillis()
          val took = now - batchStart
          val perf = math.round(100.0f * took.toFloat / (j - batchIndex)) / 100.0f
          batchStart = now
          batchIndex = j
          logger.info(s"embedding item #$j/${ids.length}, $perf items/s")
        }
        doubles
      }
      EmbeddingMap(ids, embeddings, ids.length, encoder.dim)
    }
  }

  case class BertSemanticModelConfig(
      encoder: BiEncoderConfig,
      itemFields: List[String],
      store: KnnConfig,
      selector: Selector = Selector.AcceptSelector()
  ) extends ModelConfig

  implicit val bertModelConfigDecoder: Decoder[BertSemanticModelConfig] = Decoder.instance(c =>
    for {
      encoder    <- c.downField("encoder").as[BiEncoderConfig]
      itemFields <- c.downField("itemFields").as[List[String]]
      store      <- c.downField("store").as[Option[KnnConfig]]
      selector   <- c.downField("selector").as[Option[Selector]]
    } yield {
      BertSemanticModelConfig(
        encoder = encoder,
        itemFields = itemFields,
        store = store.getOrElse(HnswConfig()),
        selector = selector.getOrElse(AcceptSelector())
      )
    }
  )

}
