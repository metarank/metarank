package ai.metarank.config

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.config.TrainConfig.FileTrainConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
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

  it should "parse redis config" in {
    val yaml = IOUtils.resourceToString("/config/redis.yml", StandardCharsets.UTF_8)
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        core = CoreConfig(),
        features = NonEmptyList.of(
          NumberFeatureSchema(FeatureName("popularity"), FieldName(Item, "popularity"), ItemScopeType)
        ),
        models = Map(
          "xgboost" -> LambdaMARTConfig(
            XGBoostConfig(10, seed = 0),
            NonEmptyList.of(FeatureName("popularity")),
            Map("click" -> 1)
          )
        ),
        api = ApiConfig(Hostname("0.0.0.0"), Port(8080)),
        state = RedisStateConfig(Hostname("localhost"), Port(6379)),
        train = FileTrainConfig("/some/path", JsonStoreFormat),
        input = None
      )
    )
  }

  it should "parse ranklens config" in {
    val yaml = Resource.my.getAsString("/ranklens/config.yml")
    val conf = parse(yaml).flatMap(_.as[Config])
    conf.isRight shouldBe true
  }

  it should "parse reference config from docs" in {
    val yaml = Resource.my.getAsString("/config/sample-config.yml")
    val conf = parse(yaml).flatMap(_.as[Config])
    conf.isRight shouldBe true
  }

  it should "fail on model referencing non-existent feature" in {
    val yaml = Resource.my.getAsString("/config/wrong-ref.yml")
    val conf = parse(yaml).flatMap(_.as[Config])
    conf.isLeft shouldBe true
  }

}
