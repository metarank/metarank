package ai.metarank.util

import ai.metarank.config.{ApiConfig, Config, Hostname, MPath, Port}
import ai.metarank.config.InputConfig._
import MPath.LocalPath
import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.config.StateConfig.MemoryStateConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import cats.data.{NonEmptyList, NonEmptyMap}

object TestConfig {
  def apply() = new Config(
    features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
    models = NonEmptyMap.of("shuffle" -> ShuffleConfig(10)),
    api = ApiConfig(Hostname("localhost"), Port(8080)),
    state = MemoryStateConfig(Port(6379)),
    input = FileInputConfig(LocalPath("/tmp/events"))
  )
}
