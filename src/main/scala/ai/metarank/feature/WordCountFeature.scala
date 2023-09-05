package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.StringField
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration._

case class WordCountFeature(schema: WordCountSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

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
        case s: StringField => Some(s)
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a string, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, SDouble(tokenCount(fieldValue.value)))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue =
    readKey(request, conf, id.id).flatMap(features.get) match {
      case Some(ScalarValue(_, _, SDouble(value), _)) => SingleValue(schema.name, value)
      case _                                       => SingleValue.missing(schema.name)
    }

  val whitespacePattern = "\\s+".r
  def tokenCount(string: String): Int = {
    whitespacePattern.split(string).length
  }
}

object WordCountFeature {
  import ai.metarank.util.DurationJson._
  case class WordCountSchema(
      name: FeatureName,
      source: FieldName,
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override def create(): IO[BaseFeature] = IO.pure(WordCountFeature(this))
  }

  implicit val wcSchemaDecoder: Decoder[WordCountSchema] =
    deriveDecoder[WordCountSchema].withErrorMessage("cannot parse a feature definition of type 'word_count'")

  implicit val wcSchemaEncoder: Encoder[WordCountSchema] = deriveEncoder
}
