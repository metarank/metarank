package ai.metarank.ml.onnx.sbert

import ai.metarank.util.Logging

object OnnxUtils extends Logging {
  def avgpool(tensor: Array[Array[Array[Float]]], tokens: Array[Array[Long]], dim: Int): Array[Array[Float]] = {
    val result = new Array[Array[Float]](tokens.length)
    var s      = 0
    while (s < tensor.length) {
      val embed = new Array[Float](dim)
      var i     = 0
      while (i < dim) {
        var sum = 0.0
        var cnt = 0
        var j   = 0
        while (j < tensor(s).length) {
          if (j < tokens(s).length) {
            sum += tensor(s)(j)(i)
            cnt += 1
          }
          j += 1
        }
        embed(i) = (sum / cnt).toFloat
        i += 1
      }
      result(s) = embed
      s += 1
    }
    result
  }

}
