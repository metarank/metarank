package ai.metarank.config

import ai.metarank.config.Selector.{
  AcceptSelector,
  AndSelector,
  FieldSelector,
  InteractionPositionSelector,
  OrSelector,
  RankingLengthSelector
}
import ai.metarank.model.Field.StringField
import ai.metarank.util.TestClickthrough
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SelectorTest extends AnyFlatSpec with Matchers {
  it should "accept with ranking length selector" in {
    val ct1 = TestClickthrough(List("p1"), List("p1"))
    val ct2 = TestClickthrough(List("p1", "p2", "p3"), List("p1"))
    RankingLengthSelector(Some(2), None).accept(ct1) shouldBe false
    RankingLengthSelector(Some(2), None).accept(ct2) shouldBe true
  }

  it should "accept events with int position selector" in {
    val ct1 = TestClickthrough(List("p1"), List("p1"))
    val ct2 = TestClickthrough(List("p1", "p2", "p3", "p4", "p5"), List("p5"))
    val ct3 = TestClickthrough(List("p1", "p2", "p3", "p4", "p5"), List("p3"))
    val ips = InteractionPositionSelector(Some(1), Some(3))
    ips.accept(ct1) shouldBe false
    ips.accept(ct2) shouldBe false
    ips.accept(ct3) shouldBe true
  }

  it should "accept events with field selector" in {
    val ct1 = TestClickthrough(List("p1"), List("p1")).copy(rankingFields = List(StringField("foo", "bar")))
    val ct2 = TestClickthrough(List("p1", "p2", "p3"), List("p1"))
    val fs  = FieldSelector("foo", "bar")
    fs.accept(ct1) shouldBe true
    fs.accept(ct2) shouldBe false
  }

  it should "accept events with and combinator" in {
    val ct1 = TestClickthrough(List("p1"), List("p1"))
      .copy(rankingFields = List(StringField("foo", "bar"), StringField("foo", "baz")))
    val ct2 = TestClickthrough(List("p1"), List("p1"))
      .copy(rankingFields = List(StringField("foo", "bar")))
    val a = AndSelector(List(FieldSelector("foo", "bar"), FieldSelector("foo", "baz")))
    a.accept(ct1) shouldBe true
    a.accept(ct2) shouldBe false
  }

  it should "accept events with or combinator" in {
    val ct1 = TestClickthrough(List("p1"), List("p1")).copy(rankingFields = List(StringField("foo", "bar")))
    val ct2 = TestClickthrough(List("p1"), List("p1")).copy(rankingFields = List(StringField("foo", "baz")))
    val ct3 = TestClickthrough(List("p1"), List("p1")).copy(rankingFields = List(StringField("foo", "qux")))
    val a   = OrSelector(List(FieldSelector("foo", "bar"), FieldSelector("foo", "baz")))
    a.accept(ct1) shouldBe true
    a.accept(ct2) shouldBe true
    a.accept(ct3) shouldBe false
  }
}
