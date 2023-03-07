package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.DiversityFeature.DiversitySchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Field.{BooleanField, NumberField, StringField, StringListField}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Dimension, Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.{SDouble, SString, SStringList}
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import cats.effect.IO
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class DiversityFeature(schema: DiversitySchema) extends ItemFeature with Logging {
  override val dim: Dimension = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO {
    event match {
      case e: ItemEvent =>
        val key = Key(ItemScope(e.item), conf.name)
        for {
          field <- event.fields.find(_.name == schema.source.field).toList
          fieldValue <- field match {
            case b: NumberField      => Option(SDouble(b.value))
            case s: StringField      => Option(SString(s.value))
            case sl: StringListField => Option(SStringList(sl.value))
            case other =>
              logger.warn(
                s"field extractor ${schema.name} expects a number/string/string[], but got $other in event $event"
              )
              None
          }
        } yield {
          Put(key, event.timestamp, fieldValue)
        }
      case _ => None
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: Event.RankItem): MValue = ???

  override def values(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      mode: BaseFeature.ValueMode
  ): List[MValue] = {
    val fieldValues = for {
      item       <- request.items.toList
      fieldValue <- features.get(Key(ItemScope(item.id), conf.name))
      scalar <- fieldValue match {
        case FeatureValue.ScalarValue(_, _, value) => Some(value)
        case _                                     => None
      }
    } yield {
      item.id -> scalar
    }
    fieldValues.headOption.map(_._2) match {
      case Some(SString(_)) | Some(SStringList(_)) =>
        val stringValues = fieldValues.collect {
          case (name, SString(value))     => name -> List(value)
          case (name, SStringList(value)) => name -> value
        }
        stringValues match {
          case Nil => emptyResponse(request)
          case nel => valuesString(request, nel.toMap)
        }
      case Some(SDouble(_)) =>
        val doubleValues = fieldValues.collect { case (name, SDouble(value)) => name -> value }
        doubleValues match {
          case Nil => emptyResponse(request)
          case nel => valuesDouble(request, nel.toMap)
        }
      case None => Nil
      case Some(other) =>
        logger.warn(s"feature ${schema.name} expected state to be string/number but got $other")
        Nil
    }
  }

  def valuesString(request: RankingEvent, features: Map[ItemId, List[String]]): List[MValue] = {
    val stringCounts = features.flatMap(_._2).groupMapReduce(identity)(_ => 1)(_ + _)
    val sum          = stringCounts.values.foldLeft(0.0)(_ + _)
    request.items.toList.map(item => {
      features.get(item.id) match {
        case None => SingleValue.missing(conf.name)
        case Some(strings) =>
          val weightsSum = strings.map(str => stringCounts.getOrElse(str, 0)).foldLeft(0)(_ + _) / sum
          SingleValue(conf.name, weightsSum)
      }
    })
  }
  def valuesDouble(request: RankingEvent, features: Map[ItemId, Double]): List[MValue] = {
    val perc = new Percentile()
    val data = features.values.toArray
    perc.setData(data)
    val median = perc.evaluate(50.0)
    request.items.toList.map(item => {
      features.get(item.id) match {
        case Some(value) => SingleValue(conf.name, value - median)
        case None        => SingleValue.missing(conf.name)
      }
    })
  }

  def emptyResponse(request: RankingEvent): List[MValue] = request.items.toList.map(_ => MValue(schema.name.value, 0.0))
}

object DiversityFeature {
  import ai.metarank.util.DurationJson._

  case class DiversitySchema(name: FeatureName, source: FieldName, ttl: Option[FiniteDuration] = None)
      extends FeatureSchema {
    val scope: ScopeType                = ItemScopeType
    val refresh: Option[FiniteDuration] = None
  }

  implicit val diversitySchemaEncoder: Encoder[DiversitySchema] = deriveEncoder
  implicit val diversitySchemaDecoder: Decoder[DiversitySchema] = deriveDecoder[DiversitySchema].ensure {
    case DiversitySchema(name, FieldName(eventType, _), _) if eventType != Item =>
      List(s"feature $name supports only item.* fields, but got $eventType")
    case _ => Nil
  }
}
