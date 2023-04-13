package ai.metarank.ml.onnx.sbert

import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}

import scala.jdk.CollectionConverters._
import java.nio.LongBuffer
import scala.collection.mutable.ArrayBuffer

case class OnnxBiEncoder(env: OrtEnvironment, session: OrtSession, tokenizer: BertFullTokenizer, dim: Int) {
  val vocab = tokenizer.getVocabulary
  val cls   = vocab.getIndex("[CLS]")
  val sep   = vocab.getIndex("[SEP]")
  val pad   = vocab.getIndex("[PAD]")

  def embed(batch: Array[String]): Array[Array[Float]] = {
    val textTokens = batch.map(sentence => tokenize(sentence))
    val maxLength  = textTokens.map(_.length).max
    val tensorSize = batch.length * maxLength
    val tokens     = new Array[Long](tensorSize)
    val tokenTypes = new Array[Long](tensorSize)
    val attMask    = new Array[Long](tensorSize)

    var s = 0
    var i = 0
    while (s < batch.length) {
      var j = 0
      while (j < math.max(maxLength, textTokens(s).length)) {
        if (j < textTokens(s).length) {
          tokens(i) = textTokens(s)(j)
          tokenTypes(i) = 0 // ???
          attMask(i) = 1
        } else {
          tokens(i) = pad
          tokenTypes(i) = 0
          attMask(i) = 0
        }
        i += 1
        j += 1
      }
      s += 1
    }
    val tensorDim = Array(batch.length.toLong, maxLength.toLong)
    val args = Map(
      "input_ids"      -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim),
      "token_type_ids" -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim),
      "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
    )
    val result     = session.run(args.asJava)
    val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
    val normalized = OnnxUtils.avgpool(tensor, textTokens, dim)
    result.close()
    args.values.foreach(_.close())
    normalized
  }

  def tokenize(sentence: String): Array[Long] = {
    val buffer = new ArrayBuffer[Long]()
    buffer.append(cls)
    tokenizer
      .tokenize(sentence)
      .asScala
      .foreach(t => {
        buffer.append(vocab.getIndex(t))
      })
    buffer.append(sep)
    buffer.toArray
  }

}

object OnnxBiEncoder {
  def apply(session: OnnxSession): OnnxBiEncoder =
    OnnxBiEncoder(session.env, session.session, session.tokenizer, session.dim)
}
