package ai.metarank.main

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config.Config
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.main.CliArgs.AutoFeatureArgs
import ai.metarank.main.command.AutoFeature
import ai.metarank.main.command.autofeature.ConfigMirror
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{Event, FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.RanklensEvents
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import io.circe.syntax._

import java.nio.file.Paths

class AutofeatureTest extends AnyFlatSpec with Matchers {
  it should "generate test config for ranklens" in {
    val args    = AutoFeatureArgs(Paths.get("/tmp"), Paths.get("/tmp"))
    val result1 = AutoFeature.run(Stream.emits(RanklensEvents()), RuleSet.stable(args)).unsafeRunSync()
    val result2 = AutoFeature.run(Stream.emits(RanklensEvents()), RuleSet.stable(args)).unsafeRunSync()
    result1.features.size shouldBe 12
  }

  it should "be deterministic" in {
    val features = NonEmptyList.fromListUnsafe[FeatureSchema](
      (0 until 100).toList.map(i =>
        NumberFeatureSchema(
          name = FeatureName(s"f$i"),
          field = FieldName(Item, s"f$i"),
          scope = ItemScopeType
        )
      )
    )
    val conf = ConfigMirror(
      features = features.toList,
      models = Map(
        "default" -> LambdaMARTConfig(
          backend = XGBoostConfig(iterations = 50),
          features = features.map(_.name),
          weights = Map("click" -> 1.0)
        )
      )
    )
    val x1 = AutoFeature.yamlFormat.pretty(conf.asJson)
    val x2 = AutoFeature.yamlFormat.pretty(conf.asJson)
    x1 shouldBe x2
  }

  it should "correctly export japanese" in {
    val conf = ConfigMirror(
      features = List(
        StringFeatureSchema(
          name = FeatureName("foo"),
          field = FieldName(Item, "foo"),
          scope = ItemScopeType,
          values = NonEmptyList.of("ﾒｲｽﾞ", "ｵﾘｼﾞﾅﾙ")
        )
      ),
      models = Map(
        "default" -> LambdaMARTConfig(
          backend = XGBoostConfig(iterations = 50),
          features = NonEmptyList.of(FeatureName("foo")),
          weights = Map("click" -> 1.0)
        )
      )
    )
    val generated = AutoFeature.yamlFormat.pretty(conf.asJson)
    val parsed    = Config.load(generated, Map.empty).unsafeRunSync()
    parsed.features shouldBe conf.features
    parsed.models shouldBe conf.models
  }
}
