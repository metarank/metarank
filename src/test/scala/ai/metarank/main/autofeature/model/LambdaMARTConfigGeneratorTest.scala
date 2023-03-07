package ai.metarank.main.autofeature.model

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.main.command.autofeature.{EventCountStat, EventModel}
import ai.metarank.main.command.autofeature.model.LambdaMARTConfigGenerator
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LambdaMARTConfigGeneratorTest extends AnyFlatSpec with Matchers {
  it should "generate model when there are rankings+events" in {
    val em       = EventModel(eventCount = EventCountStat(rankings = 100, ints = 100, intsWithRanking = 100))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val conf     = LambdaMARTConfigGenerator.maybeGenerate(em, features)
    conf shouldNot be(empty)
  }

  it should "complain when there are no rankings" in {
    val em       = EventModel(eventCount = EventCountStat(rankings = 0, ints = 100, intsWithRanking = 100))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val conf     = LambdaMARTConfigGenerator.maybeGenerate(em, features)
    conf should be(empty)
  }

  it should "complain when there are no interactions with ranking ref" in {
    val em       = EventModel(eventCount = EventCountStat(rankings = 100, ints = 100, intsWithRanking = 0))
    val features = List(NumberFeatureSchema(FeatureName("foo"), FieldName(Item, "foo"), ItemScopeType))
    val conf     = LambdaMARTConfigGenerator.maybeGenerate(em, features)
    conf should be(empty)
  }
}
