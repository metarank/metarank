package ai.metarank.model

sealed trait Dimension {
  def dim: Int
}

object Dimension {
  case class VectorDim(dim: Int) extends Dimension
  case object SingleDim extends Dimension {
    override val dim = 1
  }
}
