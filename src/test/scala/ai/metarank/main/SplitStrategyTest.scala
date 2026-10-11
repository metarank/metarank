package ai.metarank.main

import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.{CutoffSplit, FieldStrategy, RandomSplit, TimeSplit}
import ai.metarank.model.Field.StringField
import ai.metarank.model.{QueryMetadata, Timestamp}
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.model.{DatasetDescriptor, LabeledItem, Query}
import io.github.metarank.ltrlib.model.Feature.SingularFeature
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.util.Random

class SplitStrategyTest extends AnyFlatSpec with Matchers {
  it should "parse inputs" in {
    SplitStrategy.parse("random=10%") shouldBe Right(RandomSplit(10))
    SplitStrategy.parse("random") shouldBe Right(RandomSplit(80))
    SplitStrategy.parse("field=split:train:test") shouldBe Right(FieldStrategy("split", "train", "test"))
    SplitStrategy.parse("cutoff=2026-09-15T00:00:00Z") shouldBe Right(
      CutoffSplit(Instant.parse("2026-09-15T00:00:00Z"))
    )
    SplitStrategy.parse("cutoff=yesterday") shouldBe Symbol("left")
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

  // Group ids number the queries in time order, so the split can be checked by id
  def timed(n: Int) = Random
    .shuffle((0 until n).toList)
    .map(i => QueryMetadata(Query(i, Array(1.0), Array(1.0)), Timestamp(1000L * i), None, Nil))

  "cutoff split" should "test on rows at or after the cutoff" in {
    val split = CutoffSplit(Instant.ofEpochMilli(15000L)).split(desc, timed(20)).unsafeRunSync()
    split.train.groups.map(_.group).sorted shouldBe (0 until 15).toList
    split.test.groups.map(_.group).sorted shouldBe (15 until 20).toList
  }
}
