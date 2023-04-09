package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.{HuggingFaceClient, ModelHandle, SBERT}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{LocalCache, Logging}
import cats.effect.IO
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}

case class BertEncoder(sbert: SBERT) extends Encoder {
  override def dim: Int = sbert.dim
  override def encode(key: String, str: String): Array[Float] = sbert.embed(str)
  override def encode(str: String): Array[Float]              = sbert.embed(str)
}

object BertEncoder extends Logging {
  def create(model: ModelHandle, modelFile: String, vocabFile: String): IO[BertEncoder] = model match {
    case hf: HuggingFaceHandle   => loadFromHuggingFace(hf, modelFile, vocabFile).map(BertEncoder.apply)
    case local: LocalModelHandle => loadFromLocalDir(local, modelFile, vocabFile).map(BertEncoder.apply)
  }

  def loadFromHuggingFace(handle: HuggingFaceHandle, modelFile: String, vocabFile: String): IO[SBERT] = for {
    cache        <- LocalCache.create()
    modelDirName <- IO(handle.asList.mkString(File.separator))
    sbert <- HuggingFaceClient
      .create()
      .use(hf =>
        for {
          modelBytes <- cache.getIfExists(modelDirName, modelFile).flatMap {
            case Some(bytes) => IO.pure(bytes)
            case None => hf.modelFile(handle, modelFile).flatTap(bytes => cache.put(modelDirName, modelFile, bytes))
          }
          vocabBytes <- cache.getIfExists(modelDirName, vocabFile).flatMap {
            case Some(bytes) => IO.pure(bytes)
            case None => hf.modelFile(handle, vocabFile).flatTap(bytes => cache.put(modelDirName, vocabFile, bytes))
          }
        } yield {
          SBERT(
            model = new ByteArrayInputStream(modelBytes),
            dic = new ByteArrayInputStream(vocabBytes)
          )
        }
      )
  } yield {
    sbert
  }

  def loadFromLocalDir(handle: LocalModelHandle, modelFile: String, vocabFile: String): IO[SBERT] = for {
    _          <- info(s"loading $modelFile from $handle")
    modelBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + modelFile))))
    vocabBytes <- IO(IOUtils.toByteArray(new FileInputStream(new File(handle.dir + File.separator + vocabFile))))
  } yield {
    SBERT(
      model = new ByteArrayInputStream(modelBytes),
      dic = new ByteArrayInputStream(vocabBytes)
    )
  }

}
