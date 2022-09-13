package ai.metarank.main.command.autofeature

import ai.metarank.main.command.autofeature.FieldStat._
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field._

case class ItemFieldStat(
    strings: Map[String, StringFieldStat] = Map.empty,
    nums: Map[String, NumericFieldStat] = Map.empty,
    bools: Map[String, BoolFieldStat] = Map.empty
) {
  def refresh(event: ItemEvent): ItemFieldStat = {
    event.fields.foldLeft(this)((next, field) =>
      field match {
        case StringField(name, value) =>
          next.copy(strings =
            next.strings.updatedWith(name)(stat => Some(stat.getOrElse(StringFieldStat()).refresh(value)))
          )
        case NumberField(name, value) =>
          next.copy(nums = next.nums.updatedWith(name)(stat => Some(stat.getOrElse(NumericFieldStat()).refresh(value))))
        case BooleanField(name, value) =>
          next.copy(bools = next.bools.updatedWith(name)(stat => Some(stat.getOrElse(BoolFieldStat()).refresh(value))))
        case StringListField(name, values) =>
          next.copy(strings =
            values.foldLeft(strings)((acc, next) =>
              acc.updatedWith(name)(stat => Some(stat.getOrElse(StringFieldStat()).refresh(next)))
            )
          )
        case _ => next
      }
    )
  }
}
