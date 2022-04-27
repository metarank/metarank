package ai.metarank.util

import ai.metarank.config.{Config, MPath}
import ai.metarank.config.Config.{BootstrapConfig, InferenceConfig}
import ai.metarank.config.Config.ModelConfig.ShuffleConfig
import ai.metarank.config.Config.StateStoreConfig.MemConfig
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
    bootststap = BootstrapConfig(
      eventPath = MPath("/tmp"),
      workdir = MPath("/tmp")
    ),
    inference = InferenceConfig(
      port = 8080,
      host = "0.0.0.0",
      state = MemConfig(JsonCodec)
    )
  )
}
