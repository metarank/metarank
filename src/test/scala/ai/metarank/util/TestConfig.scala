package ai.metarank.util

import ai.metarank.config.{BootstrapConfig, Config, InferenceConfig, MPath}
import ai.metarank.config.EventSourceConfig.{FileSourceConfig, RestSourceConfig}
import MPath.LocalPath
import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.config.StateStoreConfig.MemConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import cats.data.{NonEmptyList, NonEmptyMap}
import io.findify.featury.values.StoreCodec.JsonCodec

object TestConfig {
  def apply() = new Config(
    features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
    models = NonEmptyMap.of("shuffle" -> ShuffleConfig(10)),
    bootstrap = BootstrapConfig(
      source = FileSourceConfig(LocalPath("/tmp/events")),
      workdir = MPath("/tmp")
    ),
    inference = InferenceConfig(
      port = 8080,
      host = "0.0.0.0",
      state = MemConfig(JsonCodec),
      source = RestSourceConfig(1000, "localhost", 8080)
    )
  )
}
