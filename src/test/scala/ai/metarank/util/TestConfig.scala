package ai.metarank.util

import ai.metarank.config.{ApiConfig, Config, EnvConfig, Hostname, Port}
import ai.metarank.config.InputConfig._
import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.config.StateStoreConfig.MemoryStateConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.{Env, FieldName}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import cats.data.{NonEmptyList, NonEmptyMap}

object TestConfig {
  def apply() = new Config(
    env = List(
      EnvConfig(
        name = Env.default,
        features = NonEmptyList.of(NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType)),
        models = NonEmptyMap.of("shuffle" -> ShuffleConfig(10))
      )
    ),
    api = ApiConfig(Hostname("localhost"), Port(8080)),
    state = MemoryStateConfig(),
    input = FileInputConfig("/tmp/events")
  )
}
