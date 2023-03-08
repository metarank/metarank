package ai.metarank.util

import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.onnxruntime.{OnnxTensor, OrtEnvironment}
import ai.onnxruntime.OrtSession.SessionOptions

import java.nio.LongBuffer
import scala.jdk.CollectionConverters._
import java.nio.file.Paths

object ONNXPlayground {

  def meanPooling(preds: Array[Array[Array[Float]]]) = {
    val result = new Array[Float](384)
    var i      = 0
    while (i < 384) {
      var sum = 0.0
      var j   = 0
      while (j < preds(0).length) {
        sum += preds(0)(j)(i)
        j += 1
      }
      result(i) = (sum / preds(0).length).toFloat
      i += 1
    }
    result
  }
  def main(args: Array[String]): Unit = {
    val vocab =
      DefaultVocabulary.builder().addFromTextFile(Paths.get("/home/shutty/work/metarank/onnx/vocab.txt")).build()
    val bert = new BertFullTokenizer(vocab, true)
    val tokensStrings = List("[CLS]") ++ bert
      .tokenize("instant noodles")
      .asScala
      .toList ++ List("[SEP]")
    val tokens  = tokensStrings.map(t => vocab.getIndex(t)).toArray
    val attmask = Array.fill(tokens.length)(1L)
    val tt      = Array.fill(tokens.length)(0L)

    val env     = OrtEnvironment.getEnvironment()
    val opts    = new SessionOptions()
    val session = env.createSession("/home/shutty/work/metarank/onnx/onnx/minilm-v2.onnx", opts)
    val tensor1 = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), Array(1, tokens.length))
    val tensor2 = OnnxTensor.createTensor(env, LongBuffer.wrap(attmask), Array(1, tokens.length))
    val tensor3 = OnnxTensor.createTensor(env, LongBuffer.wrap(tt), Array(1, tokens.length))
    val out = session.run(Map("input_ids" -> tensor1, "token_type_ids" -> tensor3, "attention_mask" -> tensor2).asJava)

    val br = 1
  }

}
