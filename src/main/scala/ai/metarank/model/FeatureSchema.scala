package ai.metarank.model

import ai.metarank.feature.FieldMatchBiencoderFeature.FieldMatchBiencoderSchema
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.DiversityFeature.DiversitySchema
import ai.metarank.feature.FieldMatchCrossEncoderFeature.FieldMatchCrossEncoderSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.feature.NumVectorFeature.VectorFeatureSchema
import ai.metarank.feature.{BaseFeature, NumVectorFeature, NumberFeature}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.PositionFeature.PositionFeatureSchema
import ai.metarank.feature.RandomFeature.RandomFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.Key.FeatureName
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}

import scala.concurrent.duration.FiniteDuration

trait FeatureSchema {
  def name: FeatureName
  def refresh: Option[FiniteDuration]
  def ttl: Option[FiniteDuration]
  def scope: ScopeType

  def create(): IO[BaseFeature]
}

object FeatureSchema {

  implicit val featureSchemaDecoder: Decoder[FeatureSchema] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      decoded <- tpe match {
        case "number"            => implicitly[Decoder[NumberFeatureSchema]].apply(c)
        case "boolean"           => implicitly[Decoder[BooleanFeatureSchema]].apply(c)
        case "string"            => implicitly[Decoder[StringFeatureSchema]].apply(c)
        case "word_count"        => implicitly[Decoder[WordCountSchema]].apply(c)
        case "rate"              => implicitly[Decoder[RateFeatureSchema]].apply(c)
        case "interacted_with"   => implicitly[Decoder[InteractedWithSchema]].apply(c)
        case "interaction_count" => implicitly[Decoder[InteractionCountSchema]].apply(c)
        case "window_count"      => implicitly[Decoder[WindowInteractionCountSchema]].apply(c)
        case "ua"                => implicitly[Decoder[UserAgentSchema]].apply(c)
        case "relevancy"         => implicitly[Decoder[RelevancySchema]].apply(c)
        case "local_time"        => implicitly[Decoder[LocalDateTimeSchema]].apply(c)
        case "item_age"          => implicitly[Decoder[ItemAgeSchema]].apply(c)
        case "field_match" =>
          val biEncoder    = implicitly[Decoder[FieldMatchBiencoderSchema]]
          val crossEncoder = implicitly[Decoder[FieldMatchCrossEncoderSchema]]
          val term         = implicitly[Decoder[FieldMatchSchema]]
          c.downField("method").downField("type").as[String] match {
            case Right("bi-encoder")    => biEncoder.apply(c)
            case Right("cross-encoder") => crossEncoder.apply(c)
            case Right("term")          => term.apply(c)
            case Right("ngram")         => term.apply(c)
            case Right("bm25")          => term.apply(c)
            case Right(other) => Left(DecodingFailure(s"term matching method $other is not supported", c.history))
            case Left(err)    => Left(err)
          }
        case "referer"   => implicitly[Decoder[RefererSchema]].apply(c)
        case "position"  => implicitly[Decoder[PositionFeatureSchema]].apply(c)
        case "vector"    => implicitly[Decoder[VectorFeatureSchema]].apply(c)
        case "random"    => implicitly[Decoder[RandomFeatureSchema]].apply(c)
        case "diversity" => implicitly[Decoder[DiversitySchema]].apply(c)
        case other       => Left(DecodingFailure(s"feature type $other is not supported", c.history))
      }
    } yield {
      decoded
    }
  )

  implicit val featureSchemaEncoder: Encoder[FeatureSchema] = Encoder.instance {
    case c: NumberFeatureSchema          => encode(c, "number")
    case c: BooleanFeatureSchema         => encode(c, "boolean")
    case c: StringFeatureSchema          => encode(c, "string")
    case c: WordCountSchema              => encode(c, "word_count")
    case c: RateFeatureSchema            => encode(c, "rate")
    case c: InteractedWithSchema         => encode(c, "interacted_with")
    case c: InteractionCountSchema       => encode(c, "interaction_count")
    case c: WindowInteractionCountSchema => encode(c, "window_count")
    case c: UserAgentSchema              => encode(c, "ua")
    case c: RelevancySchema              => encode(c, "relevancy")
    case c: LocalDateTimeSchema          => encode(c, "local_time")
    case c: ItemAgeSchema                => encode(c, "item_age")
    case c: FieldMatchSchema             => encode(c, "field_match")
    case c: RefererSchema                => encode(c, "referer")
    case c: VectorFeatureSchema          => encode(c, "vector")
    case c: RandomFeatureSchema          => encode(c, "random")
  }

  def encode[T <: FeatureSchema](c: T, name: String)(implicit enc: Encoder[T]): Json = {
    enc(c).deepMerge(Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(name)))))
  }
}
