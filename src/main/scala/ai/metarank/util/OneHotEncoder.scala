package ai.metarank.util

object OneHotEncoder {
  def fromValue(value: String, possibleValues: Seq[String], dim: Int): Array[Double] = {
    val result = new Array[Double](dim)
    val index  = possibleValues.indexOf(value)
    result(index) = 1.0
    result
  }

  def fromValues(values: Iterable[String], possibleValues: Seq[String], dim: Int) = {
    val result = new Array[Double](dim)
    for {
      value <- values
    } {
      val index = possibleValues.indexOf(value)
      if (index >= 0) {
        result(index) = 1.0
      }
    }
    result
  }

  def empty(dim: Int) = {
    new Array[Double](dim)
  }
}
