package ai.metarank.main.command.autofeature

import ai.metarank.model.Field.NumberField

sealed trait FieldStat

object FieldStat {
  case class StringFieldStat(values: Map[String, Int] = Map.empty) extends FieldStat {
    def refresh(key: String) = StringFieldStat(values.updatedWith(key)(_.map(_ + 1).orElse(Some(1))))
  }

  case class NumericFieldStat(values: List[Double] = Nil) extends FieldStat {
    def refresh(value: Double) = copy(value +: values)
  }

  case class BoolFieldStat(trues: Int = 0, falses: Int = 0) extends FieldStat {
    def refresh(value: Boolean) = if (value) copy(trues = trues + 1) else copy(falses = falses + 1)
  }

  case class NumericListFieldStat(values: List[Double] = Nil, sizes: Map[Int, Int] = Map.empty) extends FieldStat {
    def refresh(sample: List[Double]) =
      copy(values = sample ++ values, sizes = sizes.updatedWith(sample.size)(v => v.map(_ + 1).orElse(Some(1))))
  }
}
