package ai.metarank.config

import ai.metarank.config.Selector.{
  AndSelector,
  CadenceSelector,
  FieldSelector,
  InteractionPositionSelector,
  NotSelector,
  OrSelector,
  RankingLengthSelector,
  UserSelector
}
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.UserId
import ai.metarank.model.Timestamp
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

  it should "accept events with user selector" in {
    val ct1 = TestClickthrough(List("p1"), List("p1")).copy(user = Some(UserId("monitoring-bot")))
    val ct2 = TestClickthrough(List("p1"), List("p1"))
    val ct3 = TestClickthrough(List("p1"), List("p1")).copy(user = None)
    val us  = UserSelector("monitoring-bot")
    us.accept(ct1) shouldBe true
    us.accept(ct2) shouldBe false
    us.accept(ct3) shouldBe false
  }

  it should "accept events landing in the cadence slot" in {
    val cs = CadenceSelector(300, 90, 110)
    // 10:11:36 UTC -> minute 11 is 1 mod 5, so 96s into the 5-minute period
    val inSlot = TestClickthrough(List("p1", "p2"), List("p1"))
      .copy(ts = Timestamp.date(2026, 8, 19, 10, 11, 36))
    // 10:13:20 UTC -> 200s into the period
    val outOfSlot = TestClickthrough(List("p1", "p2"), List("p1"))
      .copy(ts = Timestamp.date(2026, 8, 19, 10, 13, 20))
    cs.accept(inSlot) shouldBe true
    cs.accept(outOfSlot) shouldBe false
  }

  it should "exclude synthetic traffic with cadence and ranking length combined" in {
    // the shape a monitoring bot leaves: a fixed-size ranking on a strict 5-minute tick
    val bot = TestClickthrough(List("p1", "p2"), List("p1"))
      .copy(ts = Timestamp.date(2026, 8, 19, 10, 11, 36))
    // a genuine ranking of the same size that happens to be off-cadence
    val realSmall = TestClickthrough(List("p1", "p2"), List("p1"))
      .copy(ts = Timestamp.date(2026, 8, 19, 10, 13, 20))
    // a genuine ranking on the tick, but with a full slate of items
    val realOnTick = TestClickthrough(List("p1", "p2", "p3", "p4"), List("p1"))
      .copy(ts = Timestamp.date(2026, 8, 19, 10, 11, 36))
    val keep = NotSelector(AndSelector(List(RankingLengthSelector(Some(2), Some(2)), CadenceSelector(300, 90, 110))))
    keep.accept(bot) shouldBe false
    keep.accept(realSmall) shouldBe true
    keep.accept(realOnTick) shouldBe true
  }
}
