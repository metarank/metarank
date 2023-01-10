package ai.metarank.fstore

import ai.metarank.model.Feature.BoundedListFeature
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.State.BoundedListState
import ai.metarank.model.Write.Append
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._

trait BoundedListSuite extends FeatureSuite[Append, BoundedListConfig, BoundedListFeature] {
  val config = BoundedListConfig(
    name = FeatureName("example"),
    scope = ItemScopeType,
    count = 10,
    duration = 5.hour
  )
  it should "push single element" in {
    val key    = TestKey(config, id = "p11")
    val result = write(List(Append(key, SString("foo"), now)))
    result should matchPattern {
      case Some(BoundedListValue(_, _, vals)) if vals == List(TimeValue(now, SString("foo"))) =>
    }
  }

  it should "push multiple elements" in {
    val key    = TestKey(config, id = "p12")
    val result = write(List(Append(key, SString("foo"), now), Append(key, SString("bar"), now.plus(1.second))))
    result should matchPattern {
      case Some(BoundedListValue(_, _, value))
          if value == List(TimeValue(now.plus(1.second), SString("bar")), TimeValue(now, SString("foo"))) =>
    }
  }

  it should "be bounded by element count" in {
    val key     = TestKey(config, id = "p12a")
    val appends = for { i <- 0 until config.count } yield { Append(key, SString(i.toString), now.plus(i.millis)) }
    val result  = write(appends.toList)
    result should matchPattern {
      case Some(BoundedListValue(_, _, values)) if values.size == config.count =>
    }
  }

  it should "be bounded by time" in {
    val key = TestKey(config, id = "p13")
    val appends = for { i <- (0 until config.count).reverse } yield {
      Append(key, SString(i.toString), now.minus(i.hours))
    }
    val result = write(appends.toList)
    val cutoff = now.minus(config.duration)
    result should matchPattern {
      case Some(BoundedListValue(_, _, values)) if values.forall(_.ts.isAfterOrEquals(cutoff)) =>
    }
  }


//  it should "accept state" in {
//    feature()
//      .to(fs2.Stream(BoundedListState(TestKey(config, id = "p14"), List(TimeValue(now, SString("z"))))))
//      .unsafeRunSync()
//    val state = feature().stream().compile.toList.unsafeRunSync()
//
//    state.map(_.key.scope) should contain theSameElementsAs List(
//      ItemScope(ItemId("p14")),
//      ItemScope(ItemId("p13")),
//      ItemScope(ItemId("p12")),
//      ItemScope(ItemId("p12a")),
//      ItemScope(ItemId("p11"))
//    )
//  }
}
