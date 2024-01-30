package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, RankingEvent, conf}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.{PeriodRange, PeriodicCounterConfig}
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.{PeriodicCounterValue, ScalarValue}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, RankingId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.{SString, SStringList}
import ai.metarank.model.Scope.{GlobalScope, ItemFieldScope, ItemScope, RankingFieldScope, RankingScope}
import ai.metarank.model.ScopeType.{
  GlobalScopeType,
  ItemFieldScopeType,
  ItemScopeType,
  RankingFieldScopeType,
  RankingScopeType
}
import ai.metarank.model.Write.{PeriodicIncrement, Put}
import ai.metarank.model.{
  Event,
  FeatureKey,
  FeatureSchema,
  FeatureValue,
  Field,
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

  val itemFieldFeature = ScalarConfig(
    scope = ItemScopeType,
    name = FeatureName(s"${schema.name.value}_field"),
    refresh = schema.refresh.getOrElse(0.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val rankingFieldFeature = ScalarConfig(
    scope = RankingScopeType,
    name = FeatureName(s"${schema.name.value}_rfield"),
    refresh = schema.refresh.getOrElse(0.hour),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] =
    List(topTarget, bottomTarget, topGlobal, bottomGlobal, itemFieldFeature, rankingFieldFeature)

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = {
    event match {
      case e: RankingEvent =>
        schema.scope match {
          case RankingFieldScopeType(field) =>
            IO {
              e.fieldsMap.get(field) match {
                case Some(StringListField(_, value :: _)) =>
                  List(Put(Key(RankingScope(RankingId(e.id)), rankingFieldFeature.name), e.timestamp, SString(value)))
                case Some(StringField(_, value)) =>
                  List(Put(Key(RankingScope(RankingId(e.id)), rankingFieldFeature.name), e.timestamp, SString(value)))
                case _ => Nil
              }
            }
          case _ => IO.pure(Nil)
        }
      case e: ItemEvent =>
        schema.scope match {
          case ItemFieldScopeType(field) =>
            IO {
              e.fieldsMap.get(field) match {
                case Some(StringListField(_, value :: _)) =>
                  List(Put(Key(ItemScope(e.item), itemFieldFeature.name), e.timestamp, SString(value)))
                case Some(StringField(_, value)) =>
                  List(Put(Key(ItemScope(e.item), itemFieldFeature.name), e.timestamp, SString(value)))
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
          case ScopeType.RankingFieldScopeType(fieldName) =>
            for {
              fieldFeature <- IO.fromOption(store.scalars.get(FeatureKey(RankingScopeType, rankingFieldFeature.name)))(
                new Exception(s"feature ${rankingFieldFeature.name} not found")
              )
              rankingScope <- IO
                .fromOption(e.ranking)(
                  new Exception("got interaction event grouped by ranking field, but without ranking id")
                )
                .map(id => RankingScope(RankingId(id)))
              fieldValueOption <- fieldFeature.computeValue(Key(rankingScope, rankingFieldFeature.name), e.timestamp)
              writes <- fieldValueOption match {
                case Some(ScalarValue(_, _, fieldScalar, _)) =>
                  e.`type` match {
                    case schema.top =>
                      fieldScalar match {
                        case SString(value) =>
                          makeWrite(RankingFieldScope(fieldName, value, e.item), e, topTarget, topGlobal)
                        case _ => IO.pure(Nil)
                      }

                    case schema.bottom =>
                      fieldScalar match {
                        case SString(value) =>
                          makeWrite(RankingFieldScope(fieldName, value, e.item), e, bottomTarget, bottomGlobal)
                        case _ => IO.pure(Nil)
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
          case ScopeType.ItemFieldScopeType(fieldName) =>
            for {
              fieldFeature <- IO.fromOption(store.scalars.get(FeatureKey(ItemScopeType, itemFieldFeature.name)))(
                new Exception(s"feature ${itemFieldFeature.name} not found")
              )
              fieldValueOption <- fieldFeature.computeValue(Key(ItemScope(e.item), itemFieldFeature.name), e.timestamp)
              writes <- fieldValueOption match {
                case Some(ScalarValue(_, _, fieldScalar, _)) =>
                  e.`type` match {
                    case schema.top =>
                      fieldScalar match {
                        case SString(value) => makeWrite(ItemFieldScope(fieldName, value), e, topTarget, topGlobal)
                        case _              => IO.pure(Nil)
                      }

                    case schema.bottom =>
                      fieldScalar match {
                        case SString(value) =>
                          makeWrite(ItemFieldScope(fieldName, value), e, bottomTarget, bottomGlobal)
                        case _ => IO.pure(Nil)
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
    case ItemFieldScopeType(_) => itemFieldFeature.readKeys(event)
    case _                     => Nil
  }

  override def valueKeys2(event: Event.RankingEvent, features: Map[Key, FeatureValue]): Iterable[Key] = {
    schema.scope match {
      case RankingFieldScopeType(fieldName) =>
        for {
          rankingFieldString <- event.fieldsMap.get(fieldName).toList.flatMap {
            case StringField(_, value) => Some(value)
            case _                     => None
          }
          item <- event.items.toList
          keys <- List(
            Key(RankingFieldScope(fieldName, rankingFieldString, item.id), topTarget.name),
            Key(RankingFieldScope(fieldName, rankingFieldString, item.id), bottomTarget.name),
            Key(GlobalScope, topGlobal.name),
            Key(GlobalScope, bottomGlobal.name)
          )
        } yield {
          keys
        }
      case ItemFieldScopeType(field) =>
        for {
          item       <- event.items.toList
          fieldValue <- features.get(Key(ItemScope(item.id), itemFieldFeature.name)).toList
          fieldString <- fieldValue match {
            case ScalarValue(_, _, SString(value), _) => List(value)
            case _                                    => Nil
          }
          keys <- List(
            Key(ItemFieldScope(field, fieldString), topTarget.name),
            Key(ItemFieldScope(field, fieldString), bottomTarget.name),
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
      case ItemFieldScopeType(field) =>
        features.get(Key(ItemScope(id.id), itemFieldFeature.name)) match {
          case Some(ScalarValue(_, _, SString(value), _)) => Some(ItemFieldScope(field, value))
          case _                                          => None
        }
      case RankingFieldScopeType(field) =>
        request.fieldsMap.get(field) match {
          case Some(rankingFieldValue) =>
            rankingFieldValue match {
              case StringField(_, value) => Some(RankingFieldScope(field, value, id.id))
              case _                     => None
            }
          case None => None
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
  ) extends FeatureSchema {
    override def create(): IO[BaseFeature] = IO.pure(RateFeature(this))
  }

  case class NormalizeSchema(weight: Double)

  implicit val normalizeSchemaCodec: Codec[NormalizeSchema] = deriveCodec

  implicit val rateSchemaDecoder: Decoder[RateFeatureSchema] = Decoder
    .instance(c =>
      for {
        name   <- c.downField("name").as[FeatureName]
        top    <- c.downField("top").as[String]
        bottom <- c.downField("bottom").as[String]
        scope <- c.downField("scope").as[Option[ScopeType]] match {
          case Right(Some(ItemScopeType))                => Right(ItemScopeType)
          case Right(Some(ItemFieldScopeType(field)))    => Right(ItemFieldScopeType(field))
          case Right(Some(RankingFieldScopeType(field))) => Right(RankingFieldScopeType(field))
          case Right(None)                               => Right(ItemScopeType)
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

}
