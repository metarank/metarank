package ai.metarank.config

import ai.metarank.config.InputConfig.{ApiInputConfig, FileInputConfig}
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType._
import ai.metarank.model.Key.FeatureName
import better.files.Resource
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.yaml.parser.parse
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse bootstrap config" in {
    val yaml = IOUtils.resourceToString("/config/bootstrap.yml", StandardCharsets.UTF_8)
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = NonEmptyList.of(NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType)),
        models = NonEmptyMap.of(
          "xgboost" -> LambdaMARTConfig(
            XGBoostBackend(10, seed = 0),
            NonEmptyList.of(FeatureName("price")),
            NonEmptyMap.of("click" -> 1)
          )
        ),
        api = ApiConfig(Hostname("0.0.0.0"), Port(8080)),
        state = RedisStateConfig(Hostname("localhost"), Port(6379)),
        input = FileInputConfig("s3://bucket/events/")
      )
    )
  }

  it should "parse redis config" in {
    val yaml = IOUtils.resourceToString("/config/redis.yml", StandardCharsets.UTF_8)
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = NonEmptyList.of(NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType)),
        models = NonEmptyMap.of(
          "xgboost" -> LambdaMARTConfig(
            XGBoostBackend(10, seed = 0),
            NonEmptyList.of(FeatureName("price")),
            NonEmptyMap.of("click" -> 1)
          )
        ),
        api = ApiConfig(Hostname("0.0.0.0"), Port(8080)),
        state = RedisStateConfig(Hostname("localhost"), Port(6379)),
        input = FileInputConfig("s3://bucket/events/")
      )
    )
  }

  it should "parse ranklens config" in {
    val yaml = Resource.my.getAsString("/ranklens/config.yml")
    val conf = parse(yaml).flatMap(_.as[Config])
    conf.isRight shouldBe true
  }

}
