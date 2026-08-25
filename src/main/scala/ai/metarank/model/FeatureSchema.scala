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
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}

import scala.concurrent.duration.FiniteDuration

trait FeatureSchema {
  def name: FeatureName
  def refresh: Option[FiniteDuration]
  def ttl: Option[FiniteDuration]
  def scope: ScopeType

  def create(): IO[BaseFeature]
}

object FeatureSchema {

  given featureSchemaDecoder: Decoder[FeatureSchema] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      decoded <- tpe match {
        case "number"            => summon[Decoder[NumberFeatureSchema]].apply(c)
        case "boolean"           => summon[Decoder[BooleanFeatureSchema]].apply(c)
        case "string"            => summon[Decoder[StringFeatureSchema]].apply(c)
        case "word_count"        => summon[Decoder[WordCountSchema]].apply(c)
        case "rate"              => summon[Decoder[RateFeatureSchema]].apply(c)
        case "interacted_with"   => summon[Decoder[InteractedWithSchema]].apply(c)
        case "interaction_count" => summon[Decoder[InteractionCountSchema]].apply(c)
        case "window_count"      => summon[Decoder[WindowInteractionCountSchema]].apply(c)
        case "ua"                => summon[Decoder[UserAgentSchema]].apply(c)
        case "relevancy"         => summon[Decoder[RelevancySchema]].apply(c)
        case "local_time"        => summon[Decoder[LocalDateTimeSchema]].apply(c)
        case "item_age"          => summon[Decoder[ItemAgeSchema]].apply(c)
        case "field_match" =>
          val biEncoder    = summon[Decoder[FieldMatchBiencoderSchema]]
          val crossEncoder = summon[Decoder[FieldMatchCrossEncoderSchema]]
          val term         = summon[Decoder[FieldMatchSchema]]
          c.downField("method").downField("type").as[String] match {
            case Right("bi-encoder")    => biEncoder.apply(c)
            case Right("cross-encoder") => crossEncoder.apply(c)
            case Right("term")          => term.apply(c)
            case Right("ngram")         => term.apply(c)
            case Right("bm25")          => term.apply(c)
            case Right(other) => Left(DecodingFailure(s"term matching method $other is not supported", c.history))
            case Left(err)    => Left(err)
          }
        case "referer"   => summon[Decoder[RefererSchema]].apply(c)
        case "position"  => summon[Decoder[PositionFeatureSchema]].apply(c)
        case "vector"    => summon[Decoder[VectorFeatureSchema]].apply(c)
        case "random"    => summon[Decoder[RandomFeatureSchema]].apply(c)
        case "diversity" => summon[Decoder[DiversitySchema]].apply(c)
        case other       => Left(DecodingFailure(s"feature type $other is not supported", c.history))
      }
    } yield {
      decoded
    }
  )

  given featureSchemaEncoder: Encoder[FeatureSchema] = Encoder.instance {
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

  def encode[T <: FeatureSchema](c: T, name: String)(using enc: Encoder[T]): Json = {
    enc(c).deepMerge(Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(name)))))
  }
}
