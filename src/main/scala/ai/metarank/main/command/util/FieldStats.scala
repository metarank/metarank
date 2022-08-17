package ai.metarank.main.command.util

import ai.metarank.main.command.util.FieldStats.FieldStat
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.{ClickthroughValues, FieldName}
import ai.metarank.util.Logging
import org.apache.commons.math3.stat.descriptive.rank.Percentile

import scala.collection.mutable
import scala.util.Random

case class FieldStats(fields: Map[String, FieldStat])

object FieldStats extends Logging {
  case class FieldStat(
      name: String,
      var count: Int = 0,
      var zero: Int = 0,
      var nonZero: Int = 0,
      samples: SampleBuffer = SampleBuffer()
  ) {
    def add(value: Double) = {
      count += 1
      if (value == 0.0) zero += 1 else nonZero += 1
      samples.put(value)
      this
    }
    def print() = {
      val pc = new Percentile()
      val dist =
        (10.to(90).by(10).map(q => pc.evaluate(samples.get(), q.toDouble))).toList.map(d => String.format("%.2f", d))
      logger.info(s"$name: zero=${zero} nz=${nonZero} dist=$dist")
    }
  }

  case class SampleBuffer(values: Array[Double] = new Array[Double](1024 * 32), var cursor: Int = 0) {
    def put(value: Double) = {
      if (cursor < values.length - 1) {
        cursor += 1
        values(cursor) = value
      } else {
        values(Random.nextInt(values.length)) = value
      }
      this
    }

    def get(): Array[Double] = java.util.Arrays.copyOf(values, cursor)
  }

  def apply(ctv: List[ClickthroughValues]) = {
    val fields = mutable.Map[String, FieldStat]()
    for {
      ct        <- ctv
      itemValue <- ct.values
      value     <- itemValue.values
    } {
      value match {
        case SingleValue(name, value) =>
          fields.put(name.value, fields.getOrElse(name.value, FieldStat(name.value)).add(value))
        case VectorValue(name, values, dim) =>
          val stat = fields.getOrElse(name.value, FieldStat(name.value))
          values.foreach(v => stat.add(v))
          fields.put(name.value, stat)
        case CategoryValue(name, cat, index) =>
          fields.put(name.value, fields.getOrElse(name.value, FieldStat(name.value)).add(index))
      }
    }
    new FieldStats(fields.toMap)
  }
}
