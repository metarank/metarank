package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.StringFeature.MethodName.{IndexMethodName, OnehotMethodName}
import ai.metarank.feature.StringFeature.{EncodeMethod, IndexEncodeMethod, OnehotEncodeMethod, StringFeatureSchema}
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import ai.metarank.model.Identifier._
import ai.metarank.util.{Logging, OneHotEncoder}
import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, SStringList, ScalarValue}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class StringFeature(schema: StringFeatureSchema) extends ItemFeature with Logging {
  val encoder = schema.encode match {
    case Some(OnehotMethodName) | None =>
      OnehotEncodeMethod(
        names = schema.values.toList.map(value => s"${schema.name}_$value"),
        possibleValues = schema.values.toList,
        dim = schema.values.size
      )
    case Some(IndexMethodName) =>
      IndexEncodeMethod(
        name = schema.name,
        possibleValues = schema.values.toList
      )
  }
  override def dim: Int = encoder.dim

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.source)

  override def writes(event: Event, fields: FieldStore): Iterable[Put] = {
    for {
      key   <- keyOf(event)
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

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key   <- keyOf(request, Some(id.id))
      value <- features.get(key)
    } yield {
      value
    }
    result match {
      case Some(ScalarValue(_, _, SStringList(values))) => encoder.encode(values)
      case _                                            => encoder.encode(Nil)
    }
  }

}

object StringFeature {
  import ai.metarank.util.DurationJson._

  sealed trait EncodeMethod {
    def dim: Int
    def encode(values: Seq[String]): MValue
  }

  case class OnehotEncodeMethod(names: List[String], possibleValues: List[String], dim: Int) extends EncodeMethod {
    override def encode(values: Seq[String]): VectorValue =
      VectorValue(names, OneHotEncoder.fromValues(values, possibleValues, dim), dim)
  }
  case class IndexEncodeMethod(name: String, possibleValues: List[String]) extends EncodeMethod {
    override val dim = 1
    override def encode(values: Seq[String]): CategoryValue = {
      values.headOption match {
        case Some(first) =>
          val index = possibleValues.indexOf(first)
          CategoryValue(name, index + 1) // zero is
        case None =>
          CategoryValue(name, 0)
      }
    }
  }

  sealed trait MethodName
  object MethodName {
    case object OnehotMethodName extends MethodName
    case object IndexMethodName  extends MethodName
    implicit val methodNameDecoder: Decoder[MethodName] = Decoder.decodeString.emapTry {
      case "index"  => Success(IndexMethodName)
      case "onehot" => Success(OnehotMethodName)
      case other    => Failure(new Exception(s"string encoding method $other is not supported"))
    }
  }

  case class StringFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      encode: Option[MethodName] = None,
      values: NonEmptyList[String],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val stringSchemaDecoder: Decoder[StringFeatureSchema] = deriveDecoder

}
