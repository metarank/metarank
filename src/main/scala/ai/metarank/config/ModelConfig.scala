package ai.metarank.config

import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.ml.rank.{LambdaMARTRanker, NoopRanker, ShuffleRanker}
import ai.metarank.ml.rank.NoopRanker.NoopConfig
import ai.metarank.ml.rank.ShuffleRanker.ShuffleConfig
import ai.metarank.ml.recommend.{MFRecommender, TrendingRecommender}
import ai.metarank.ml.recommend.TrendingRecommender.TrendingConfig
import ai.metarank.ml.recommend.mf.ALSRecImpl
import ai.metarank.ml.recommend.mf.ALSRecImpl.ALSConfig
import ai.metarank.model.Key.FeatureName
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto._

import scala.concurrent.duration._
import scala.util.Random

trait ModelConfig {
  def selector: Selector
}

object ModelConfig {
  import ai.metarank.util.DurationJson._

  implicit val modelConfigEncoder: Encoder[ModelConfig] = Encoder.instance {
    case lm: LambdaMARTConfig => LambdaMARTRanker.lmEncoder(lm).deepMerge(withType("lambdamart"))
    case s: ShuffleConfig     => ShuffleRanker.shuffleEncoder(s).deepMerge(withType("shuffle"))
    case n: NoopConfig        => NoopRanker.noopEncoder(n).deepMerge(withType("noop"))
    case n: TrendingConfig    => TrendingRecommender.trendingConfigCodec(n).deepMerge(withType("trending"))
    case n: ALSConfig         => ALSRecImpl.alsConfigEncoder(n).deepMerge(withType("als"))
  }

  implicit val modelConfigDecoder: Decoder[ModelConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Right("lambdamart") => LambdaMARTRanker.lmDecoder.tryDecode(c)
      case Right("shuffle")    => ShuffleRanker.shuffleDecoder.tryDecode(c)
      case Right("noop")       => NoopRanker.noopDecoder.tryDecode(c)
      case Right("trending")   => TrendingRecommender.trendingConfigCodec.tryDecode(c)
      case Right("als")        => ALSRecImpl.alsConfigDecoder.tryDecode(c)
      case Right(other)        => Left(DecodingFailure(s"cannot decode model $other", c.history))
      case Left(err)           => Left(err)
    }
  )

  def withType(tpe: String): Json = Json.fromJsonObject(JsonObject.fromIterable(List("type" -> Json.fromString(tpe))))

}
