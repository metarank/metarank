package ai.metarank.model

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.LocalDateTimeFeature.LocalDateTimeSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.Key.FeatureName
import io.circe.{Codec, Decoder, DecodingFailure}

import scala.concurrent.duration.FiniteDuration

trait FeatureSchema {
  def name: FeatureName
  def refresh: Option[FiniteDuration]
  def ttl: Option[FiniteDuration]
  def scope: ScopeType
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
        case "field_match"       => implicitly[Decoder[FieldMatchSchema]].apply(c)
        case "referer"           => implicitly[Decoder[RefererSchema]].apply(c)
        case other               => Left(DecodingFailure(s"feature type $other is not supported", c.history))
      }
    } yield {
      decoded
    }
  )
}
