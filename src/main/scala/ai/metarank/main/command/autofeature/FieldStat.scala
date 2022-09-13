package ai.metarank.main.command.autofeature

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
