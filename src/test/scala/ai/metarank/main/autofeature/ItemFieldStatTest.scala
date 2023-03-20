package ai.metarank.main.autofeature

import ai.metarank.main.command.autofeature.FieldStat.{NumericFieldStat, NumericListFieldStat, StringFieldStat}
import ai.metarank.main.command.autofeature.ItemFieldStat
import ai.metarank.model.Field.{NumberField, NumberListField, StringField, StringListField}
import ai.metarank.util.TestItemEvent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ItemFieldStatTest extends AnyFlatSpec with Matchers {
  it should "accept events with string field" in {
    val stat1 = ItemFieldStat().refresh(TestItemEvent("p1", List(StringListField("color", List("red", "green")))))
    stat1.strings.get("color") shouldBe Some(StringFieldStat(Map("red" -> 1, "green" -> 1)))
  }

  it should "accept events with numeric field" in {
    val stat = ItemFieldStat().refresh(TestItemEvent("p1", List(NumberField("price", 10.0))))
    stat.nums.get("price") shouldBe Some(NumericFieldStat(List(10.0)))
  }

  it should "detect num list fields" in {
    val stat = ItemFieldStat().refresh(TestItemEvent("p1", List(NumberListField("grades", Array(1.0, 2.0, 3.0)))))
    stat.nums.get("grades") shouldBe None
    stat.numlists.get("grades") shouldBe Some(NumericListFieldStat(List(1.0, 2.0, 3.0), Map(3 -> 1)))
  }
}
