package ai.metarank.ml.onnx

import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.metarank.util.Logging
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession, TensorInfo}
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.jdk.CollectionConverters._
import java.io.{InputStream, InputStreamReader}
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets

case class SBERT(env: OrtEnvironment, session: OrtSession, tokenizer: BertFullTokenizer, dim: Int) {
  def embed(sentence: String): Array[Float] = {
    val tokenStrings = List.concat(
      List("[CLS]"),
      tokenizer.tokenize(sentence).asScala.toList,
      List("[SEP]")
    )
    val tokens        = tokenStrings.map(t => tokenizer.getVocabulary.getIndex(t)).toArray
    val attentionMask = Array.fill(tokens.length)(1L)
    val tokenTypes    = Array.fill(tokens.length)(0L)
    val result = session.run(
      Map(
        "input_ids"      -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), Array(1, tokens.length)),
        "token_type_ids" -> OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), Array(1, tokens.length)),
        "attention_mask" -> OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), Array(1, tokens.length))
      ).asJava
    )
    val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
    val normalized = avgpool(tensor, dim)
    normalized
  }

  def avgpool(tensor: Array[Array[Array[Float]]], dim: Int): Array[Float] = {
    val result = new Array[Float](dim)
    var i      = 0
    while (i < dim) {
      var sum = 0.0
      var j   = 0
      while (j < tensor(0).length) {
        sum += tensor(0)(j)(i)
        j += 1
      }
      result(i) = (sum / tensor(0).length).toFloat
      i += 1
    }
    result
  }
}

object SBERT extends Logging {
  def apply(model: InputStream, dic: InputStream): SBERT = {
    val tokens    = IOUtils.toString(dic, StandardCharsets.UTF_8).split('\n')
    val vocab     = DefaultVocabulary.builder().add(tokens.toList.asJava).build()
    val tokenizer = new BertFullTokenizer(vocab, true)
    val env       = OrtEnvironment.getEnvironment("sbert")
    val opts      = new SessionOptions()
    opts.setIntraOpNumThreads(Runtime.getRuntime.availableProcessors())
    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    val modelBytes = IOUtils.toByteArray(model)
    val session    = env.createSession(modelBytes)
    val desc       = session.getMetadata.getGraphName
    val size       = FileUtils.byteCountToDisplaySize(modelBytes.length)
    val inputs     = session.getInputNames.asScala.toList
    val outputs    = session.getOutputNames.asScala.toList
    val dim = session.getOutputInfo.asScala
      .get("last_hidden_state")
      .flatMap(_.getInfo.asInstanceOf[TensorInfo].getShape.lastOption)
      .getOrElse(0L)
    logger.info(s"Loaded ONNX model (size=$size inputs=$inputs outputs=$outputs dim=$dim)")
    SBERT(env, session, tokenizer, dim.toInt)
  }
}
