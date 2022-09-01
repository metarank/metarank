package ai.metarank.model

import ai.metarank.config.ModelConfig.ModelBackend.{LightGBMBackend, XGBoostBackend}
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import ai.metarank.config.{Config, ModelConfig, StateStoreConfig}
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
import ai.metarank.main.CliArgs
import ai.metarank.main.CliArgs.{ImportArgs, ServeArgs, StandaloneArgs, TrainArgs, ValidateArgs}
import ai.metarank.model.AnalyticsPayload.{SystemParams, UsedFeature}
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.Version
import com.google.common.hash.Hashing
import io.circe.Codec
import io.circe.generic.semiauto._
import org.apache.commons.io.IOUtils

import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters._
import java.net.NetworkInterface
import java.util.stream.Collectors
import scala.util.Try

case class AnalyticsPayload(
    state: String,
    modelTypes: List[String],
    usedFeatures: List[UsedFeature],
    system: SystemParams,
    mode: String,
    version: Option[String],
    ts: Long
)

object AnalyticsPayload {
  case class SystemParams(os: String, arch: String, jvm: String, macHash: Option[String])

  case class UsedFeature(name: FeatureName, `type`: String)

  implicit val systemCodec: Codec[SystemParams]               = deriveCodec
  implicit val usedFeatureCodec: Codec[UsedFeature]           = deriveCodec
  implicit val analyticsPayloadCodec: Codec[AnalyticsPayload] = deriveCodec

  def apply(config: Config, args: CliArgs): AnalyticsPayload =
    new AnalyticsPayload(
      mode = args match {
        case _: ServeArgs      => "serve"
        case _: ImportArgs     => "import"
        case _: StandaloneArgs => "standalone"
        case _: TrainArgs      => "train"
        case _: ValidateArgs   => "validate"
      },
      version = Version(),
      state = config.state match {
        case _: RedisStateConfig  => "redis"
        case _: MemoryStateConfig => "memory"
      },
      modelTypes = config.models.values.map {
        case LambdaMARTConfig(_: LightGBMBackend, _, _) => "lambdamart-lightgbm"
        case LambdaMARTConfig(_: XGBoostBackend, _, _)  => "lambdamart-xgboost"
        case ShuffleConfig(_)                           => "shuffle"
        case NoopConfig()                               => "noop"
      }.toList,
      usedFeatures = config.features.toList.map {
        case f: RateFeatureSchema            => UsedFeature(f.name, "rate")
        case f: BooleanFeatureSchema         => UsedFeature(f.name, "boolean")
        case f: FieldMatchSchema             => UsedFeature(f.name, "field_match")
        case f: InteractedWithSchema         => UsedFeature(f.name, "interacted_with")
        case f: InteractionCountSchema       => UsedFeature(f.name, "interaction_count")
        case f: ItemAgeSchema                => UsedFeature(f.name, "item_age")
        case f: LocalDateTimeSchema          => UsedFeature(f.name, "local_time")
        case f: NumberFeatureSchema          => UsedFeature(f.name, "number")
        case f: RefererSchema                => UsedFeature(f.name, "referer")
        case f: RelevancySchema              => UsedFeature(f.name, "relevancy")
        case f: StringFeatureSchema          => UsedFeature(f.name, "string")
        case f: UserAgentSchema              => UsedFeature(f.name, "ua")
        case f: WindowInteractionCountSchema => UsedFeature(f.name, "window_count")
        case f: WordCountSchema              => UsedFeature(f.name, "word_count")
      },
      system = SystemParams(
        os = System.getProperty("os.name"),
        arch = System.getProperty("os.arch"),
        jvm = System.getProperty("java.version"),
        macHash = getMacHash
      ),
      ts = System.currentTimeMillis()
    )

  def getMacHash: Option[String] = {
    val interfaces = NetworkInterface
      .networkInterfaces()
      .collect(Collectors.toList[NetworkInterface])
      .asScala
      .filter(iface => iface.isUp && !iface.isLoopback && !iface.isVirtual)
      .flatMap(iface => Option(iface.getHardwareAddress))

    interfaces.headOption
      .map(addr => Hashing.sha256().hashBytes(addr).toString)
  }
}
