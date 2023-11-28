package ai.metarank.ml.onnx.sbert

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}

import scala.jdk.CollectionConverters._
import java.nio.LongBuffer
import scala.collection.mutable.ArrayBuffer

case class OnnxBiEncoder(env: OrtEnvironment, session: OrtSession, tokenizer: HuggingFaceTokenizer, dim: Int) {

  def embed(batch: Array[String]): Array[Array[Float]] = {
    val encoded = tokenizer.batchEncode(batch)

    val tokens       = encoded.flatMap(e => e.getIds)
    val tokenLengths = encoded.map(e => e.getAttentionMask.sum.toInt)
    val tokenTypes   = encoded.flatMap(e => e.getTypeIds)
    val attMask      = encoded.flatMap(e => e.getAttentionMask)

    val tensorDim = Array(batch.length.toLong, encoded(0).getIds.length)
    val args =
      Map(
        "input_ids"      -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim),
        "token_type_ids" -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim),
        "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
      )
    val result     = session.run(args.asJava)
    val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
    val normalized = avgpool(tensor, tokenLengths, dim)
    result.close()
    args.values.foreach(_.close())
    normalized
  }

  def avgpool(tensor: Array[Array[Array[Float]]], tokenLengths: Array[Int], dim: Int): Array[Array[Float]] = {
    val result = new Array[Array[Float]](tokenLengths.length)
    var s      = 0
    while (s < tensor.length) {
      val embed = new Array[Float](dim)
      var i     = 0
      while (i < dim) {
        var sum = 0.0
        var cnt = 0
        var j   = 0
        while (j < tensor(s).length) {
          if (j < tokenLengths(s)) {
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

object OnnxBiEncoder {
  def apply(session: OnnxSession): OnnxBiEncoder =
    OnnxBiEncoder(session.env, session.session, session.tokenizer, session.dim)
}
