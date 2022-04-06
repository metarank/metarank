package ai.metarank.config

import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.Item
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.FeatureName
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class ConfigTest extends AnyFlatSpec with Matchers {
  it should "fail on empty features" in {
    Try(Config.validateConfig(Config(Nil, Nil)).unsafeRunSync()).isFailure shouldBe true
  }

  it should "fail on duplicates" in {
    val dupe = StringFeatureSchema("foo", FieldName(Item, "foo"), ItemScope, NonEmptyList.of("x"))
    Try(Config.validateConfig(new Config(List(dupe, dupe), Nil)).unsafeRunSync()).isFailure shouldBe true
  }
}
