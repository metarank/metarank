package ai.metarank.main

import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.{FieldStrategy, RandomSplit, TimeSplit}
import ai.metarank.model.Field.StringField
import ai.metarank.model.{QueryMetadata, Timestamp}
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.model.{DatasetDescriptor, LabeledItem, Query}
import io.github.metarank.ltrlib.model.Feature.SingularFeature
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitStrategyTest extends AnyFlatSpec with Matchers {
  it should "parse inputs" in {
    SplitStrategy.parse("random=10%") shouldBe Right(RandomSplit(10))
    SplitStrategy.parse("random") shouldBe Right(RandomSplit(80))
    SplitStrategy.parse("field=split:train:test") shouldBe Right(FieldStrategy("split", "train", "test"))
  }

  val desc  = DatasetDescriptor(List(SingularFeature("foo")))
  val now   = Timestamp.now
  val query = QueryMetadata(Query(desc, List(LabeledItem(1.0, 1, Array(1.0)))), now, None, Nil)

  "time-split" should "handle unbalanced small inputs, size=1" in {
    val split = TimeSplit(80).split(desc, List(query, query)).unsafeRunSync()
    split.test.groups.size shouldBe 1
    split.train.groups.size shouldBe 1
  }

  it should "handle unbalanced small inputs, size=2" in {
    val split = TimeSplit(80).split(desc, List(query, query)).unsafeRunSync()
    split.test.groups.size shouldBe 1
    split.train.groups.size shouldBe 1
  }

  it should "handle unbalanced small inputs, size=3" in {
    val split = TimeSplit(80).split(desc, List(query, query, query)).unsafeRunSync()
    split.test.groups.size shouldBe 1
    split.train.groups.size shouldBe 2
  }

  "field split" should "split by field value" in {
    val result = FieldStrategy("split", "train", "test")
      .split(
        desc,
        List(
          query.copy(fields = List(StringField("split", "train"))),
          query.copy(fields = List(StringField("split", "test")))
        )
      )
      .unsafeRunSync()
    result.test.groups.size shouldBe 1
    result.train.groups.size shouldBe 1
  }
}
