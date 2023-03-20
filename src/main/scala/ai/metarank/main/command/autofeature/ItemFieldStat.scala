package ai.metarank.main.command.autofeature

import ai.metarank.main.command.autofeature.FieldStat._
import ai.metarank.model.Event.{ItemEvent, RankingEvent}
import ai.metarank.model.{Event, Field}
import ai.metarank.model.Field._

case class ItemFieldStat(
    strings: Map[String, StringFieldStat] = Map.empty,
    nums: Map[String, NumericFieldStat] = Map.empty,
    bools: Map[String, BoolFieldStat] = Map.empty,
    numlists: Map[String, NumericListFieldStat] = Map.empty
) {
  def refresh(event: ItemEvent): ItemFieldStat = {
    event.fields.foldLeft(this)((next, field) => next.refresh(field))
  }

  def refresh(event: RankingEvent): ItemFieldStat = {
    event.items.toList.flatMap(_.fields).foldLeft(this)((next, field) => next.refresh(field))
  }

  def refresh(field: Field): ItemFieldStat = {
    field match {
      case s: StringField  => refresh(s)
      case b: BooleanField => refresh(b)
      case n: NumberField  => refresh(n)
      case StringListField(name, values) =>
        values.foldLeft(this)((acc, value) => acc.refresh(StringField(name, value)))
      case n: NumberListField => refresh(n)
    }
  }

  def refresh(field: StringField): ItemFieldStat = {
    val updated = strings.getOrElse(field.name, StringFieldStat()).refresh(field.value)
    copy(strings = strings + (field.name -> updated))
  }

  def refresh(field: BooleanField): ItemFieldStat = {
    val updated = bools.getOrElse(field.name, BoolFieldStat()).refresh(field.value)
    copy(bools = bools + (field.name -> updated))
  }

  def refresh(field: NumberField): ItemFieldStat = {
    val updated = nums.getOrElse(field.name, NumericFieldStat()).refresh(field.value)
    copy(nums = nums + (field.name -> updated))
  }

  def refresh(field: NumberListField): ItemFieldStat = {
    val updated = numlists.getOrElse(field.name, NumericListFieldStat()).refresh(field.value.toList)
    copy(numlists = numlists + (field.name -> updated))
  }
}
