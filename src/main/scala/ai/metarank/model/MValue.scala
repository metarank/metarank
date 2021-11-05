package ai.metarank.model

sealed trait MValue {
  def name: String
  def dim: Int
}

object MValue {
  case class SingleValue(name: String, value: Double) extends MValue {
    override def dim: Int = 1
  }

  case class VectorValue(name: String, values: Array[Double]) extends MValue {
    override def dim: Int = values.length
  }
}
