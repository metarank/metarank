package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.{IndexCategoricalEncoder, OnehotCategoricalEncoder, StringFeatureSchema}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.{SingleDim, VectorDim}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.FieldName.EventType.Ranking
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.Scalar.SStringList
import ai.metarank.model.Write.Put
import ai.metarank.model.{Dimension, Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.util.{Logging, OneHotEncoder}
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class StringFeature(schema: StringFeatureSchema) extends ItemFeature with Logging {
  val encoder = schema.encode match {
    case Some(OnehotEncoderName) | None =>
      OnehotCategoricalEncoder(
        name = schema.name,
        possibleValues = schema.values.toList,
        dim = VectorDim(schema.values.size)
      )
    case Some(IndexEncoderName) =>
      IndexCategoricalEncoder(
        name = schema.name,
        possibleValues = schema.values.toList
      )
  }
  override def dim = encoder.dim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO {
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.source.field)
      fieldValue <- field match {
        case StringField(_, value)     => Some(SStringList(List(value)))
        case StringListField(_, value) => Some(SStringList(value))
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a string or string[], but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, fieldValue)
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    readKey(request, conf, id.id).flatMap(features.get) match {
      case Some(ScalarValue(_, _, SStringList(values))) => encoder.encode(values)
      case _                                            => encoder.encode(Nil)
    }
  }

  override def values(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      mode: BaseFeature.ValueMode
  ): List[MValue] = {
    schema.source match {
      case FieldName(Ranking, field) =>
        val const = request.fields.find(_.name == field) match {
          case Some(StringField(_, value))      => encoder.encode(List(value))
          case Some(StringListField(_, values)) => encoder.encode(values)
          case _                                => encoder.encode(Nil)
        }
        request.items.toList.map(_ => const)
      case _ =>
        request.items.toList.map(item => {
          val fieldOverride = item.fields.collectFirst {
            case StringField(name, value) if name == schema.source.field      => List(value)
            case StringListField(name, values) if name == schema.source.field => values
          }
          fieldOverride match {
            case Some(over) => encoder.encode(over)
            case None       => value(request, features, item)
          }
        })

    }
  }

}

object StringFeature {
  import ai.metarank.util.DurationJson._

  sealed trait CategoricalEncoder {
    def dim: Dimension
    def encode(values: Seq[String]): MValue
  }

  case class OnehotCategoricalEncoder(name: FeatureName, possibleValues: List[String], dim: VectorDim)
      extends CategoricalEncoder {
    override def encode(values: Seq[String]): VectorValue =
      VectorValue(name, OneHotEncoder.fromValues(values, possibleValues, dim.dim), dim)
  }
  case class IndexCategoricalEncoder(name: FeatureName, possibleValues: List[String]) extends CategoricalEncoder {
    override val dim = SingleDim
    override def encode(values: Seq[String]): CategoryValue = {
      values.headOption match {
        case Some(first) =>
          val index = possibleValues.indexOf(first)
          CategoryValue(name, first, index + 1) // zero is
        case None =>
          CategoryValue(name, "nil", 0)
      }
    }
  }

  sealed trait EncoderName {
    def name: String
  }
  object EncoderName {
    case object OnehotEncoderName extends EncoderName {
      val name = "onehot"
    }
    case object IndexEncoderName extends EncoderName {
      val name = "index"
    }
    implicit val methodNameDecoder: Decoder[EncoderName] = Decoder.decodeString.emapTry {
      case IndexEncoderName.name  => Success(IndexEncoderName)
      case OnehotEncoderName.name => Success(OnehotEncoderName)
      case other                  => Failure(new Exception(s"string encoding method $other is not supported"))
    }
    implicit val methodNameEncoder: Encoder[EncoderName] = Encoder.encodeString.contramap(_.name)
  }

  case class StringFeatureSchema(
      name: FeatureName,
      source: FieldName,
      scope: ScopeType,
      encode: Option[EncoderName] = None,
      values: NonEmptyList[String],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val stringSchemaDecoder: Decoder[StringFeatureSchema] =
    deriveDecoder[StringFeatureSchema].withErrorMessage("cannot parse a feature definition of type 'string'")
  implicit val stringSchemaEncoder: Encoder[StringFeatureSchema] = deriveEncoder[StringFeatureSchema]
}
