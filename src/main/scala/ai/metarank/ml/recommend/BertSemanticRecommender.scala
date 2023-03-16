package ai.metarank.ml.recommend

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.onnx.SBERT
import ai.metarank.ml.recommend.KnnConfig.HnswConfig
import ai.metarank.ml.recommend.MFRecommender.EmbeddingSimilarityModel
import ai.metarank.ml.recommend.embedding.HnswJavaIndex.{HnswIndexReader, HnswIndexWriter, HnswOptions}
import ai.metarank.ml.recommend.embedding.{EmbeddingMap, HnswJavaIndex, KnnIndex}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{FieldName, TrainValues}
import ai.metarank.model.TrainValues.ItemValues
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.io.file.Path
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder

object BertSemanticRecommender {
  case class BertSemanticPredictor(name: String, config: BertSemanticModelConfig)
      extends RecommendPredictor[BertSemanticModelConfig, EmbeddingSimilarityModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[EmbeddingSimilarityModel] = for {
      encoder    <- Encoder.create(config.encoder)
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

    def embed(items: List[ItemValues], fieldSet: Set[String], encoder: Encoder): IO[EmbeddingMap] = IO {
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
      } yield {
        val floats  = encoder.encode(item.item, stringFields.mkString(" "))
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
      encoder: EncoderType,
      itemFields: List[String],
      store: KnnConfig,
      selector: Selector = Selector.AcceptSelector()
  ) extends ModelConfig

  sealed trait EncoderType
  object EncoderType {
    case class BertEncoderType(model: String) extends EncoderType
    case class CsvEncoderType(path: String)   extends EncoderType

    implicit val bertDecoder: Decoder[BertEncoderType] = deriveDecoder[BertEncoderType]
    implicit val csvDecoder: Decoder[CsvEncoderType]   = deriveDecoder[CsvEncoderType]
    implicit val encoderDecoder: Decoder[EncoderType] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(err)     => Left(err)
        case Right("bert") => bertDecoder.tryDecode(c)
        case Right("csv")  => csvDecoder.tryDecode(c)
        case Right(other)  => Left(DecodingFailure(s"cannot decode embedding type $other", c.history))
      }
    )
  }

  implicit val bertModelConfigDecoder: Decoder[BertSemanticModelConfig] = Decoder.instance(c =>
    for {
      encoder    <- c.downField("encoder").as[EncoderType]
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

  sealed trait Encoder {
    def encode(id: ItemId, str: String): Array[Float]
    def dim: Int
  }

  object Encoder {
    case class BertEncoder(sbert: SBERT) extends Encoder {
      override def dim: Int                                      = sbert.dim
      override def encode(id: ItemId, str: String): Array[Float] = sbert.embed(str)
    }

    object BertEncoder {
      def create(model: String): IO[BertEncoder] = IO {
        val sbert = SBERT(
          model = this.getClass.getResourceAsStream(s"/sbert/$model.onnx"),
          dic = this.getClass.getResourceAsStream("/sbert/sentence-transformer/vocab.txt")
        )
        BertEncoder(sbert)
      }
    }
    case class CsvEncoder(dic: Map[ItemId, Array[Float]], dim: Int) extends Encoder {
      override def encode(id: ItemId, str: String): Array[Float] = dic.get(id) match {
        case Some(value) => value
        case None        => new Array[Float](dim)
      }
    }

    object CsvEncoder extends Logging {
      def create(lines: fs2.Stream[IO, String]) = for {
        dic <- lines
          .filter(_.nonEmpty)
          .evalMap(line => IO.fromEither(parseLine(line)))
          .compile
          .toList
        _ <- info(s"loaded ${dic.size} embeddings")
        size <- IO(dic.map(_._2.length).distinct).flatMap {
          case Nil        => IO.raiseError(new Exception("no embeddings found"))
          case one :: Nil => IO.pure(one)
          case other      => IO.raiseError(new Exception(s"all embedding sizes should be the same, but got $other"))
        }
      } yield {
        CsvEncoder(dic.toMap, size)
      }
      def create(path: String): IO[CsvEncoder] = {
        create(fs2.io.file.Files[IO].readUtf8Lines(Path(path)))
      }
    }

    def create(conf: EncoderType) = conf match {
      case EncoderType.BertEncoderType(model) => BertEncoder.create(model)
      case EncoderType.CsvEncoderType(path)   => CsvEncoder.create(path)

    }

    def parseLine(line: String): Either[Throwable, (ItemId, Array[Float])] = {
      val tokens = line.split(',')
      if (tokens.length > 1) {
        val key    = ItemId(tokens(0))
        val values = new Array[Float](tokens.length - 1)
        var i      = 1
        var failed = false
        while ((i < tokens.length) && !failed) {
          tokens(i).toFloatOption match {
            case Some(float) => values(i - 1) = float
            case None        => failed = true
          }
          i += 1
        }
        if (failed) Left(new Exception(s"cannot parse line $line")) else Right((key, values))
      } else {
        Left(new Exception("cannot parse embedding"))
      }
    }
  }

}