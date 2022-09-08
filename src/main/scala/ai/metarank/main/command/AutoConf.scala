package ai.metarank.main.command

import ai.metarank.main.CliArgs.AutoConfArgs
import ai.metarank.main.command.AutoConf.FieldStat.{BoolFieldStat, NumericFieldStat, StringFieldStat}
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field
import ai.metarank.model.Field.{BooleanField, NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

import scala.collection.mutable

object AutoConf {
  sealed trait FieldStat
  object FieldStat {
    case class StringFieldStat(values: Map[String, Int] = Map.empty) extends FieldStat {
      def refresh(key: String) = StringFieldStat(values.updatedWith(key)(_.map(_ + 1).orElse(Some(1))))
    }
    case class NumericFieldStat(values: List[Double] = Nil) extends FieldStat {
      def refresh(key: Double) = NumericFieldStat(key +: values)
    }
    case class BoolFieldStat(trues: Int = 0, falses: Int = 0) extends FieldStat {
      def refresh(value: Boolean) = if (value) copy(trues = trues + 1) else copy(falses = falses + 1)
    }
  }
  case class Item(
      id: ItemId,
      strings: Map[String, StringFieldStat],
      nums: Map[String, NumericFieldStat],
      bools: Map[String, BoolFieldStat]
  ) {
    def refresh(event: ItemEvent) = for {
      eventField <- event.fields
    } yield {
      eventField match {
        case StringField(name, value) =>
          copy(strings = strings.updatedWith(name)(stat => Some(stat.getOrElse(StringFieldStat()).refresh(value))))
        case NumberField(name, value) =>
          copy(nums = nums.updatedWith(name)(stat => Some(stat.getOrElse(NumericFieldStat()).refresh(value))))
        case BooleanField(name, value) =>
          copy(bools = bools.updatedWith(name)(stat => Some(stat.getOrElse(BoolFieldStat()).refresh(value))))
        case StringListField(name, values) =>
          copy(strings =
            values.foldLeft(strings)((acc, next) =>
              acc.updatedWith(name)(stat => Some(stat.getOrElse(StringFieldStat()).refresh(next)))
            )
          )
      }
    }
  }

  case class EventModel(items: Map[ItemId, Item])

  object EventModel {
    def buil
  }

  def run(args: AutoConfArgs): IO[Unit] = ???
}
