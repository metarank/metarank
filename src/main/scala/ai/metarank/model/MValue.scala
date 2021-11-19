package ai.metarank.model

sealed trait MValue {
  def dim: Int
}

object MValue {
  case class SingleValue(name: String, value: Double) extends MValue {
    override def dim: Int = 1
  }

  case class VectorValue(names: List[String], values: Array[Double], dim: Int) extends MValue
}
