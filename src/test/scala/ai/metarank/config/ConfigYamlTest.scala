package ai.metarank.config

import ai.metarank.config.BootstrapConfig.SyntheticImpressionConfig
import ai.metarank.config.EventSourceConfig.{FileSourceConfig, RestSourceConfig}
import ai.metarank.config.MPath.{LocalPath, S3Path}
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.config.StateStoreConfig.MemConfig
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType._
import better.files.Resource
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.yaml.parser.parse
import io.findify.featury.values.StoreCodec.JsonCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigYamlTest extends AnyFlatSpec with Matchers {
  it should "parse example config" in {
    val yaml =
      """bootstrap:
        |  source:
        |    type: file
        |    path: file:///tmp/events
        |  workdir: s3://bucket/prefix
        |inference:
        |  host: localhost
        |  port: 8080
        |  source:
        |    type: rest
        |  state:
        |    type: memory
        |    format: json
        |features:
        |  - name: price
        |    type: number
        |    scope: item
        |    source: metadata.price
        |models:
        |  test:
        |    type: lambdamart
        |    path: /tmp/model.dat
        |    backend: 
        |      type: xgboost
        |      iterations: 10
        |      seed: 0
        |    features: [ price ]
        |    weights:
        |      click: 1""".stripMargin
    val conf = parse(yaml).flatMap(_.as[Config])
    conf shouldBe Right(
      Config(
        features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
        models = NonEmptyMap.of(
          "test" -> LambdaMARTConfig(
            MPath("/tmp/model.dat"),
            XGBoostBackend(10, seed = 0),
            NonEmptyList.of("price"),
            NonEmptyMap.of("click" -> 1)
          )
        ),
        bootstrap = BootstrapConfig(
          source = FileSourceConfig(LocalPath("/tmp/events")),
          workdir = S3Path("bucket", "prefix")
        ),
        inference = InferenceConfig(
          port = 8080,
          host = "localhost",
          state = MemConfig(JsonCodec),
          source = RestSourceConfig(10000, "localhost", 8080)
        )
      )
    )
  }

  it should "parse ranklens config" in {
    val yaml = Resource.my.getAsString("/ranklens/config.yml")
    val conf = parse(yaml).flatMap(_.as[Config])
    conf.isRight shouldBe true
  }

  it should "parse bootstrap config with custom impression inject" in {
    val yaml = """  source:
                 |    type: file
                 |    path: file:///tmp/events
                 |  workdir: s3://bucket/prefix
                 |  syntheticImpression:
                 |    enabled: true
                 |    eventName: foo
                 |""".stripMargin
    val conf = parse(yaml).flatMap(_.as[BootstrapConfig])
    conf shouldBe Right(
      BootstrapConfig(
        source = FileSourceConfig(LocalPath("/tmp/events")),
        workdir = S3Path("bucket", "prefix"),
        syntheticImpression = SyntheticImpressionConfig(true, "foo")
      )
    )
  }
}
