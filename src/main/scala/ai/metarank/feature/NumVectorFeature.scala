package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.NumVectorFeature.Reducer._
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{NumberField, NumberListField}
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.{SDouble, SDoubleList}
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder, Json}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

case class NumVectorFeature(schema: VectorFeatureSchema) extends ItemFeature with Logging {
  val reducers     = schema.reduce.getOrElse(List(Min, Max, Size, Avg))
  override val dim = VectorDim(reducers.size)

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, fields: Persistence): IO[Iterable[Put]] = IO {
    val br = 1
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.source.field)
      values <- field match {
        case n: NumberField     => Some(List(n.value))
        case n: NumberListField => Some(n.value)
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a numeric field, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, SDoubleList(reducers.map(_.reduce(values))))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key <- readKey(request, conf, id.id)
      value <- features.get(key) match {
        case Some(ScalarValue(_, _, SDoubleList(values))) => Some(VectorValue(schema.name, values.toArray, dim))
        case _                                            => None
      }
    } yield {
      value
    }
    result.getOrElse(VectorValue.empty(schema.name, dim))
  }

}

object NumVectorFeature {
  sealed trait Reducer {
    def name: String
    def reduce(values: List[Double]): Double
  }
  object Reducer {
    object First extends Reducer {
      val name                                          = "first"
      override def reduce(values: List[Double]): Double = values.headOption.getOrElse(0.0)
    }

    object Last extends Reducer {
      val name                                          = "last"
      override def reduce(values: List[Double]): Double = values.lastOption.getOrElse(0.0)
    }

    object Min extends Reducer {
      val name                                          = "min"
      override def reduce(values: List[Double]): Double = values.minOption.getOrElse(0.0)
    }

    object Max extends Reducer {
      val name                                          = "max"
      override def reduce(values: List[Double]): Double = values.maxOption.getOrElse(0.0)
    }

    object Avg extends Reducer {
      val name = "avg"

      override def reduce(values: List[Double]): Double = {
        val size = values.size
        if (size > 0) values.sum / size else 0.0
      }
    }

    object Random extends Reducer {
      val name                                          = "random"
      override def reduce(values: List[Double]): Double = scala.util.Random.shuffle(values).headOption.getOrElse(0.0)
    }

    object Sum extends Reducer {
      val name                                          = "sum"
      override def reduce(values: List[Double]): Double = values.reduceLeftOption(_ + _).getOrElse(0.0)
    }

    object Size extends Reducer {
      val name                                          = "size"
      override def reduce(values: List[Double]): Double = values.length
    }

    object EuclideanDistance extends Reducer {
      val name = "euclidean_distance"

      override def reduce(values: List[Double]): Double =
        values.reduceLeftOption((acc, next) => acc + next * next) match {
          case None      => 0.0
          case Some(sum) => math.sqrt(sum)
        }
    }

    val reducers = List(First, Last, Min, Max, Avg, Random, Sum, Size, EuclideanDistance).map(r => r.name -> r).toMap

    implicit val reducerEncoder: Encoder[Reducer] = Encoder.instance(r => Json.fromString(r.name))
    implicit val reducerDecoder: Decoder[Reducer] = Decoder.decodeString.emapTry { s =>
      reducers.get(s) match {
        case Some(value) => Success(value)
        case None        => Failure(new Exception(s"reducer $s is not supported"))
      }
    }
  }

  import ai.metarank.util.DurationJson._

  case class VectorFeatureSchema(
      name: FeatureName,
      source: FieldName,
      reduce: Option[List[Reducer]] = None,
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val vectorSchemaCodec: Codec[VectorFeatureSchema] = deriveCodec
}
