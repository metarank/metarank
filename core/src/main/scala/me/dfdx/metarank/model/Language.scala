package me.dfdx.metarank.model

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.ArrayBuffer

case class Language(code: String, analyzer: Analyzer) {
  def tokenize(input: String): List[String] = {
    val stream = analyzer.tokenStream("none", input)
    stream.reset()
    val term   = stream.addAttribute(classOf[CharTermAttribute])
    val buffer = ArrayBuffer[String]()
    while (stream.incrementToken()) {
      buffer += term.toString
    }
    stream.close()
    buffer.toList
  }
}

object Language {
  lazy val English = Language("en", new EnglishAnalyzer())

  def fromCode(code: String): Either[LanguageLoadingError, Language] = code match {
    case "en" => Right(English)
    case _    => Left(LanguageLoadingError(code))
  }

  case class LanguageLoadingError(code: String) extends Exception(s"language $code is not supported")
}
