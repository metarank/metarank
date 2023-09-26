package ai.metarank.ml.onnx.sbert

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.djl.util.PairList
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

case class OnnxCrossEncoder(env: OrtEnvironment, session: OrtSession, tokenizer: HuggingFaceTokenizer) {

  def encode(batch: Array[SentencePair]): Array[Float] = {
    if (batch.length == 0) {
      Array.empty
    } else {
      val encoded = tokenizer.batchEncode(new PairList(batch.map(_.a).toList.asJava, batch.map(_.b).toList.asJava))

      val tokens     = encoded.flatMap(e => e.getIds)
      val tokenTypes = encoded.flatMap(e => e.getTypeIds)
      val attMask    = encoded.flatMap(e => e.getAttentionMask)

      val tensorDim = Array(batch.length.toLong, encoded(0).getIds.length)
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

}

object OnnxCrossEncoder extends Logging {
  case class SentencePair(a: String, b: String)
  case class TokenTypeMask(tokens: Array[Long], types: Array[Long], attmask: Array[Long])

  def apply(session: OnnxSession): OnnxCrossEncoder =
    OnnxCrossEncoder(session.env, session.session, session.tokenizer)

}
