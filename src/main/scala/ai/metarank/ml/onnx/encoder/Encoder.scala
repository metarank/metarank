package ai.metarank.ml.onnx.encoder

import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.sbert.{OnnxBiEncoder, OnnxSession}
import ai.metarank.ml.onnx.{EmbeddingCache, ModelHandle}
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO

//trait Encoder {
//  def encode(key: String, str: String): Option[Array[Float]]
//  def encode(str: String): Option[Array[Float]]
//  def dim: Int
//}
//
//object Encoder {
//  def create(conf: EncoderType): IO[Encoder] = conf match {
//    case EncoderType.CrossEncoderType(model, cache, modelFile, vocabFile) =>
//      for {
//        session <- createSession(model, modelFile, vocabFile)
//      } yield {
//        ???
//      }
//    case EncoderType.BiEncoderType(model, itemCache, rankCache, modelFile, vocabFile) =>
//      for {
//        session <- createSession(model, modelFile, vocabFile)
//        items <- itemCache match {
//          case Some(path) => EmbeddingCache.fromCSV(path, ',', session.dim)
//          case None       => IO.pure(EmbeddingCache.empty())
//        }
//        fields <- rankCache match {
//          case Some(path) => EmbeddingCache.fromCSV(path, ',', session.dim)
//          case None       => IO.pure(EmbeddingCache.empty())
//        }
//      } yield {
//        CachedEncoder(items, fields, BiEncoder(OnnxBiEncoder(session)))
//      }
//
//  }
//
//  def createSession(model: ModelHandle, modelFile: String, vocabFile: String): IO[OnnxSession] = model match {
//    case hf: HuggingFaceHandle   => OnnxSession.loadFromHuggingFace(hf, modelFile, vocabFile)
//    case local: LocalModelHandle => OnnxSession.loadFromLocalDir(local, modelFile, vocabFile)
//  }
//
//}
