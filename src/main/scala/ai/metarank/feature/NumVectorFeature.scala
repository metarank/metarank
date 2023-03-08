package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.NumVectorFeature.Reducer._
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.Event.RankItem
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
  override val dim = VectorDim(reducers.map(_.dim).sum)

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
      values <- field match {
        case n: NumberField     => Some(List(n.value))
        case n: NumberListField => Some(n.value)
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a numeric field, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, SDoubleList(reducers.flatMap(_.reduce(values))))
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
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
    result.getOrElse(VectorValue.missing(schema.name, dim))
  }

}

object NumVectorFeature {
  sealed trait Reducer {
    def name: String
    def dim: Int
    def reduce(values: Array[Double]): Array[Double]
  }
  object Reducer {
    object First extends Reducer {
      val name                                  = "first"
      val dim                                   = 1
      override def reduce(values: List[Double]) = List(values.headOption.getOrElse(0.0))
    }

    object Last extends Reducer {
      val name                                  = "last"
      val dim                                   = 1
      override def reduce(values: List[Double]) = List(values.lastOption.getOrElse(0.0))
    }

    object Min extends Reducer {
      val name                                  = "min"
      val dim                                   = 1
      override def reduce(values: List[Double]) = List(values.minOption.getOrElse(0.0))
    }

    object Max extends Reducer {
      val name                                  = "max"
      val dim                                   = 1
      override def reduce(values: List[Double]) = List(values.maxOption.getOrElse(0.0))
    }

    object Avg extends Reducer {
      val name = "avg"
      val dim  = 1
      override def reduce(values: List[Double]) = {
        val size = values.size
        if (size > 0) List(values.sum / size) else List(0.0)
      }
    }

    object Random extends Reducer {
      val dim                                   = 1
      val name                                  = "random"
      override def reduce(values: List[Double]) = List(scala.util.Random.shuffle(values).headOption.getOrElse(0.0))
    }

    object Sum extends Reducer {
      val dim                                   = 1
      val name                                  = "sum"
      override def reduce(values: List[Double]) = List(values.reduceLeftOption(_ + _).getOrElse(0.0))
    }

    object Size extends Reducer {
      val dim                                   = 1
      val name                                  = "size"
      override def reduce(values: List[Double]) = List(values.length)
    }

    object EuclideanDistance extends Reducer {
      val name = "euclidean_distance"
      val dim  = 1
      override def reduce(values: List[Double]) =
        values.reduceLeftOption((acc, next) => acc + next * next) match {
          case None      => List(0.0)
          case Some(sum) => List(math.sqrt(sum))
        }
    }

    case class VectorReducer(dim: Int) extends Reducer {
      val name = s"vector$dim"

      override def reduce(values: List[Double]): List[Double] = {
        val first = values.take(dim)
        val size  = first.size
        if (size < dim) {
          first ++ List.fill(dim - size)(0)
        } else {
          first
        }
      }

    }

    val reducers = List(First, Last, Min, Max, Avg, Random, Sum, Size, EuclideanDistance).map(r => r.name -> r).toMap
    val vectorPattern                             = "vector([0-9]+)".r
    implicit val reducerEncoder: Encoder[Reducer] = Encoder.instance(r => Json.fromString(r.name))
    implicit val reducerDecoder: Decoder[Reducer] = Decoder.decodeString.emapTry { s =>
      reducers.get(s) match {
        case Some(value) => Success(value)
        case None =>
          s match {
            case vectorPattern(dim) => Success(VectorReducer(dim.toInt))
            case "vectorN" | "vectorn" =>
              Failure(new Exception("The N in vectorN reducer should be a number. Like vector9."))
            case _ => Failure(new Exception(s"reducer $s is not supported"))
          }
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
