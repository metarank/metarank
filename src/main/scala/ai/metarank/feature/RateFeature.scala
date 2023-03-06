package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, conf}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.{PeriodicCounterValue, ScalarValue}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.{SString, SStringList}
import ai.metarank.model.Scope.{FieldScope, GlobalScope, ItemScope}
import ai.metarank.model.ScopeType.{FieldScopeType, GlobalScopeType, ItemScopeType}
import ai.metarank.model.Write.{PeriodicIncrement, Put}
import ai.metarank.model.{
  Event,
  FeatureKey,
  FeatureSchema,
  FeatureValue,
  FieldName,
  Key,
  MValue,
  Scope,
  ScopeType,
  Write
}
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RateFeature(schema: RateFeatureSchema) extends ItemFeature with Logging {
  override val dim = VectorDim(schema.periods.size)

  val topGlobal = PeriodicCounterConfig(
    scope = GlobalScopeType,
    name = FeatureName(s"${schema.name.value}_${schema.top}_norm"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(1.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val bottomGlobal = PeriodicCounterConfig(
    scope = GlobalScopeType,
    name = FeatureName(s"${schema.name.value}_${schema.bottom}_norm"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(1.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val topTarget = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.top}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(1.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val bottomTarget = PeriodicCounterConfig(
    scope = schema.scope,
    name = FeatureName(s"${schema.name.value}_${schema.bottom}"),
    period = schema.bucket,
    sumPeriodRanges = schema.periods.map(p => PeriodRange(p, 0)),
    refresh = schema.refresh.getOrElse(1.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val fieldScope = ScalarConfig(
    scope = ItemScopeType,
    name = FeatureName(s"${schema.name.value}_field"),
    refresh = schema.refresh.getOrElse(1.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(topTarget, bottomTarget, topGlobal, bottomGlobal, fieldScope)

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = {
    event match {
      case e: ItemEvent =>
        schema.scope match {
          case ItemScopeType => IO.pure(None)
          case FieldScopeType(field) =>
            IO {
              e.fieldsMap.get(field) match {
                case Some(StringListField(_, value :: _)) =>
                  List(Put(Key(ItemScope(e.item), fieldScope.name), e.timestamp, SString(value)))
                case Some(StringField(_, value)) =>
                  List(Put(Key(ItemScope(e.item), fieldScope.name), e.timestamp, SString(value)))
                case _ => Nil
              }
            }
          case _ => IO.pure(Nil)
        }
      case e: InteractionEvent =>
        schema.scope match {
          case ScopeType.ItemScopeType if e.`type` == schema.top =>
            makeWrite(ItemScope(e.item), e, topTarget, topGlobal)
          case ScopeType.ItemScopeType if e.`type` == schema.bottom =>
            makeWrite(ItemScope(e.item), e, bottomTarget, bottomGlobal)
          case ScopeType.FieldScopeType(fieldName) =>
            for {
              fieldFeature <- IO.fromOption(store.scalars.get(FeatureKey(ItemScopeType, fieldScope.name)))(
                new Exception(s"feature ${fieldScope.name} not found")
              )
              fieldValueOption <- fieldFeature.computeValue(Key(ItemScope(e.item), fieldScope.name), e.timestamp)
              writes <- fieldValueOption match {
                case Some(ScalarValue(_, _, fieldScalar)) =>
                  e.`type` match {
                    case schema.top =>
                      fieldScalar match {
                        case SString(value) => makeWrite(FieldScope(fieldName, value), e, topTarget, topGlobal)
                        case _              => IO.pure(Nil)
                      }

                    case schema.bottom =>
                      fieldScalar match {
                        case SString(value) => makeWrite(FieldScope(fieldName, value), e, bottomTarget, bottomGlobal)
                        case _              => IO.pure(Nil)
                      }

                    case _ => IO.pure(Nil)
                  }
                case Some(other) =>
                  warn(s"feature ${schema.name.value} expects field '$fieldName' to be string, but got $other") *> IO
                    .pure(
                      Nil
                    )
                case None => IO.pure(Nil)
              }
            } yield {
              writes
            }
          case _ => IO.pure(Nil)
        }
      case _ => IO.pure(Nil)
    }
  }

  def makeWrite(
      scope: Scope,
      event: InteractionEvent,
      topCounter: PeriodicCounterConfig,
      globalCounter: PeriodicCounterConfig
  ): IO[Iterable[Write]] = IO {
    schema.normalize match {
      case Some(_) =>
        List(
          PeriodicIncrement(Key(scope, topCounter.name), event.timestamp, 1),
          PeriodicIncrement(Key(GlobalScope, globalCounter.name), event.timestamp, 1)
        )
      case None =>
        List(PeriodicIncrement(Key(scope, topCounter.name), event.timestamp, 1))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = schema.scope match {
    case FieldScopeType(_) => fieldScope.readKeys(event)
    case _                 => Nil
  }

  override def valueKeys2(event: Event.RankingEvent, features: Map[Key, FeatureValue]): Iterable[Key] = {
    schema.scope match {
      case FieldScopeType(field) =>
        for {
          item       <- event.items.toList
          fieldValue <- features.get(Key(ItemScope(item.id), fieldScope.name)).toList
          fieldString <- fieldValue match {
            case ScalarValue(_, _, SString(value)) => List(value)
            case _                                 => Nil
          }
          keys <- List(
            Key(FieldScope(field, fieldString), topTarget.name),
            Key(FieldScope(field, fieldString), bottomTarget.name),
            Key(GlobalScope, topGlobal.name),
            Key(GlobalScope, bottomGlobal.name)
          )
        } yield {
          keys
        }

      case ItemScopeType =>
        event.items.toList.flatMap(item =>
          List(
            Key(ItemScope(item.id), topTarget.name),
            Key(ItemScope(item.id), bottomTarget.name),
            Key(GlobalScope, topGlobal.name),
            Key(GlobalScope, bottomGlobal.name)
          )
        )
      case _ => Nil
    }
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = {
    val targetScopeOption = schema.scope match {
      case ItemScopeType => Some(ItemScope(id.id))
      case FieldScopeType(field) =>
        features.get(Key(ItemScope(id.id), fieldScope.name)) match {
          case Some(ScalarValue(_, _, SString(value))) => Some(FieldScope(field, value))
          case _                                       => None
        }
      case _ => None
    }
    schema.normalize match {
      case None =>
        val result = for {
          targetScope <- targetScopeOption
          topValue    <- features.get(Key(targetScope, topTarget.name))
          bottomValue <- features.get(Key(targetScope, bottomTarget.name))
          topNum      <- topValue.cast[PeriodicCounterValue] if topNum.values.length == dim.dim
          bottomNum   <- bottomValue.cast[PeriodicCounterValue] if bottomNum.values.length == dim.dim
        } yield {
          val values = new Array[Double](dim.dim)
          var i      = 0
          while (i < dim.dim) {
            values(i) = topNum.values(i).value / bottomNum.values(i).value.toDouble
            i += 1
          }
          VectorValue(schema.name, values, dim)
        }
        result.getOrElse(VectorValue.missing(schema.name, dim))
      case Some(norm) =>
        val result = for {
          targetScope       <- targetScopeOption
          topValue          <- features.get(Key(targetScope, topTarget.name))
          bottomValue       <- features.get(Key(targetScope, bottomTarget.name))
          topGlobalValue    <- features.get(Key(GlobalScope, topGlobal.name))
          bottomGlobalValue <- features.get(Key(GlobalScope, bottomGlobal.name))
          topNum            <- topValue.cast[PeriodicCounterValue] if topNum.values.length == dim.dim
          bottomNum         <- bottomValue.cast[PeriodicCounterValue] if bottomNum.values.length == dim.dim
          topGlobalNum      <- topGlobalValue.cast[PeriodicCounterValue] if topGlobalNum.values.length == dim.dim
          bottomGlobalNum   <- bottomGlobalValue.cast[PeriodicCounterValue] if bottomGlobalNum.values.length == dim.dim
        } yield {
          val values = new Array[Double](dim.dim)
          var i      = 0
          while (i < dim.dim) {
            values(i) = (norm.weight + topNum.values(i).value) / (norm.weight * (bottomGlobalNum
              .values(i)
              .value / topGlobalNum.values(i).value) + bottomNum.values(i).value)
            i += 1
          }

          VectorValue(schema.name, values, dim)
        }
        result.getOrElse(VectorValue.missing(schema.name, dim))
    }
  }
}

object RateFeature {
  import ai.metarank.util.DurationJson._
  case class RateFeatureSchema(
      name: FeatureName,
      top: String,
      bottom: String,
      scope: ScopeType,
      bucket: FiniteDuration,
      periods: List[Int],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None,
      normalize: Option[NormalizeSchema] = None
  ) extends FeatureSchema

  case class NormalizeSchema(weight: Double)

  implicit val normalizeSchemaCodec: Codec[NormalizeSchema] = deriveCodec

  implicit val rateSchemaDecoder: Decoder[RateFeatureSchema] = Decoder
    .instance(c =>
      for {
        name   <- c.downField("name").as[FeatureName]
        top    <- c.downField("top").as[String]
        bottom <- c.downField("bottom").as[String]
        scope <- c.downField("scope").as[Option[ScopeType]] match {
          case Right(Some(ItemScopeType))         => Right(ItemScopeType)
          case Right(Some(FieldScopeType(field))) => Right(FieldScopeType(field))
          case Right(None)                        => Right(ItemScopeType)
          case Right(other) => Left(DecodingFailure(s"scope $other is not supported for rate feature $name", c.history))
          case Left(error)  => Left(error)
        }
        bucket    <- c.downField("bucket").as[FiniteDuration]
        periods   <- c.downField("periods").as[List[Int]]
        refresh   <- c.downField("refresh").as[Option[FiniteDuration]]
        ttl       <- c.downField("ttl").as[Option[FiniteDuration]]
        normalize <- c.downField("normalize").as[Option[NormalizeSchema]]
      } yield {
        RateFeatureSchema(
          name = name,
          top = top,
          bottom = bottom,
          scope = scope,
          bucket = bucket,
          periods = periods,
          refresh = refresh,
          ttl = ttl,
          normalize = normalize
        )
      }
    )
    .withErrorMessage("cannot parse a feature definition of type 'rate'")

  implicit val rateSchemaEncoder: Encoder[RateFeatureSchema] = deriveEncoder

  def isItemScope(schema: RateFeatureSchema): Boolean = schema.scope match {
    case ScopeType.ItemScopeType         => true
    case ScopeType.FieldScopeType(field) => true
    case _                               => false
  }
}
