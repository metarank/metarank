package ai.metarank.ml.onnx.sbert

import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.metarank.ml.onnx.sbert.OnnxCrossEncoder.{SentencePair, TokenTypeMask}
import ai.metarank.util.Logging
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession, TensorInfo}
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.InputStream
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

case class OnnxCrossEncoder(env: OrtEnvironment, session: OrtSession, tokenizer: BertFullTokenizer) {
  val vocab = tokenizer.getVocabulary
  val cls   = vocab.getIndex("[CLS]")
  val sep   = vocab.getIndex("[SEP]")
  val pad   = vocab.getIndex("[PAD]")

  def encode(batch: Array[SentencePair]): Array[Float] = {
    if (batch.length == 0) {
      Array.empty
    } else {
      val encoded    = batch.map(sp => tokenize(sp))
      val maxLength  = encoded.map(_.tokens.length).max
      val tensorSize = batch.length * maxLength
      val tokens     = new Array[Long](tensorSize)
      val tokenTypes = new Array[Long](tensorSize)
      val attMask    = new Array[Long](tensorSize)

      var s = 0
      var i = 0
      while (s < batch.length) {
        var j = 0
        while (j < maxLength) {
          if (j < encoded(s).tokens.length) {
            tokens(i) = encoded(s).tokens(j)
            tokenTypes(i) = encoded(s).types(j)
            attMask(i) = encoded(s).attmask(j)
          } else {
            tokens(i) = pad
            tokenTypes(i) = pad
            attMask(i) = pad
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
      val result = session.run(args.asJava)
      val tensor = result.get(0).getValue.asInstanceOf[Array[Array[Float]]]
      val logits = new Array[Float](batch.length)
      var j      = 0
      while (j < batch.length) {
        logits(j) = tensor(j)(0)
        j += 1
      }
      result.close()
      args.values.foreach(_.close())
      logits
    }
  }

  def tokenize(sentence: SentencePair): TokenTypeMask = {
    val tokenBuffer = new ArrayBuffer[Long]()
    val typeBuffer  = new ArrayBuffer[Long]()
    val maskBuffer  = new ArrayBuffer[Long]()
    tokenBuffer.append(cls)
    typeBuffer.append(0L)
    maskBuffer.append(1L)
    tokenizer
      .tokenize(sentence.a)
      .asScala
      .foreach(t => {
        tokenBuffer.append(vocab.getIndex(t))
        typeBuffer.append(0L)
        maskBuffer.append(1L)
      })
    tokenBuffer.append(sep)
    typeBuffer.append(0L)
    maskBuffer.append(1L)
    tokenizer
      .tokenize(sentence.b)
      .asScala
      .foreach(t => {
        tokenBuffer.append(vocab.getIndex(t))
        typeBuffer.append(1L)
        maskBuffer.append(1L)
      })
    tokenBuffer.append(sep)
    typeBuffer.append(1L)
    maskBuffer.append(1L)
    TokenTypeMask(tokenBuffer.toArray, typeBuffer.toArray, maskBuffer.toArray)
  }

}

object OnnxCrossEncoder extends Logging {
  case class SentencePair(a: String, b: String)
  case class TokenTypeMask(tokens: Array[Long], types: Array[Long], attmask: Array[Long])

  def apply(session: OnnxSession): OnnxCrossEncoder =
    OnnxCrossEncoder(session.env, session.session, session.tokenizer)

}
