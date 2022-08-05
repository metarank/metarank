package ai.metarank.fstore


import ai.metarank.model.Feature.BoundedList.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Append
import ai.metarank.util.TestKey

import scala.concurrent.duration._

trait BoundedListSuite extends FeatureSuite[Append] {
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
    val result = write(List(Append(key, SString("foo"), now), Append(key, SString("bar"), now)))
    result should matchPattern {
      case Some(BoundedListValue(_, _, value))
          if value == List(TimeValue(now, SString("bar")), TimeValue(now, SString("foo"))) =>
    }
  }

  it should "be bounded by element count" in {
    val key     = TestKey(config, id = "p12a")
    val appends = for { i <- 0 until config.count } yield { Append(key, SString(i.toString), now) }
    val result  = write(appends.toList)
    result should matchPattern {
      case Some(BoundedListValue(_, _, values)) if values.size == config.count =>
    }
  }

  it should "be bounded by time" in {
    val key     = TestKey(config, id = "p13")
    val appends = for { i <- (0 until config.count).reverse } yield { Append(key, SString(i.toString), now.minus(i.hours)) }
    val result  = write(appends.toList)
    val cutoff  = now.minus(config.duration)
    result should matchPattern {
      case Some(BoundedListValue(_, _, values)) if values.forall(_.ts.isAfterOrEquals(cutoff)) =>
    }
  }
}
