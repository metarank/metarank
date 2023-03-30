package ai.metarank.util

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.ar.ArabicAnalyzer
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer
import org.apache.lucene.analysis.core.WhitespaceTokenizer
import org.apache.lucene.analysis.cz.CzechAnalyzer
import org.apache.lucene.analysis.da.DanishAnalyzer
import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.analysis.el.GreekAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.es.SpanishAnalyzer
import org.apache.lucene.analysis.et.EstonianAnalyzer
import org.apache.lucene.analysis.fi.FinnishAnalyzer
import org.apache.lucene.analysis.fr.FrenchAnalyzer
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer
import org.apache.lucene.analysis.it.ItalianAnalyzer
import org.apache.lucene.analysis.ja.JapaneseAnalyzer
import org.apache.lucene.analysis.nl.DutchAnalyzer
import org.apache.lucene.analysis.no.NorwegianAnalyzer
import org.apache.lucene.analysis.pl.PolishAnalyzer
import org.apache.lucene.analysis.pt.PortugueseAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.sv.SwedishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tr.TurkishAnalyzer
import org.apache.lucene.analysis.{Analyzer, Tokenizer}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success}

case class TextAnalyzer(makeAnalyzer: () => Analyzer, names: NonEmptyList[String]) {
  lazy val analyzer = makeAnalyzer()
  def split(line: String): Array[String] = {
    val stream = analyzer.tokenStream("ye", line)
    stream.reset()
    val term   = stream.addAttribute(classOf[CharTermAttribute])
    val buffer = ArrayBuffer[String]()
    while (stream.incrementToken()) {
      val next = term.toString
      buffer.append(next)
    }
    stream.close()
    buffer.toArray
  }
}

object TextAnalyzer {
  def apply(make: => Analyzer, name: String)                = new TextAnalyzer(() => make, NonEmptyList.of(name))
  def apply(make: => Analyzer, alias: String, name: String) = new TextAnalyzer(() => make, NonEmptyList.of(alias, name))
  def create(language: String): Either[Throwable, TextAnalyzer] =
    analyzers.find(_.names.toList.contains(language)) match {
      case Some(value) => Right(value)
      case None =>
        Left(
          new Exception(s"language $language is not yet supported. Please, file an issue on github for it to be added.")
        )
    }

  def analyzer1(tok: Tokenizer) = new Analyzer() {
    override def createComponents(fieldName: String): Analyzer.TokenStreamComponents =
      new TokenStreamComponents(tok)
  }
  lazy val icu        = TextAnalyzer(analyzer1(new ICUTokenizer()), "icu")
  lazy val whitespace = TextAnalyzer(analyzer1(new WhitespaceTokenizer()), "whitespace")
  lazy val english    = TextAnalyzer(new EnglishAnalyzer(), "en", "english")

  lazy val analyzers = List(
    TextAnalyzer(new StandardAnalyzer(), "generic"),
    english,
    TextAnalyzer(new CzechAnalyzer(), "cz", "czech"),
    TextAnalyzer(new DanishAnalyzer(), "da", "danish"),
    TextAnalyzer(new DutchAnalyzer(), "nl", "dutch"),
    TextAnalyzer(new EstonianAnalyzer(), "et", "estonian"),
    TextAnalyzer(new FinnishAnalyzer(), "fi", "finnish"),
    TextAnalyzer(new FrenchAnalyzer(), "fr", "french"),
    TextAnalyzer(new GermanAnalyzer(), "de", "german"),
    TextAnalyzer(new GreekAnalyzer(), "gr", "greek"),
    TextAnalyzer(new ItalianAnalyzer(), "it", "italian"),
    TextAnalyzer(new NorwegianAnalyzer(), "no", "norwegian"),
    TextAnalyzer(new PolishAnalyzer(), "pl", "polish"),
    TextAnalyzer(new PortugueseAnalyzer(), "pt", "portuguese"),
    TextAnalyzer(new SpanishAnalyzer(), "es", "spanish"),
    TextAnalyzer(new SwedishAnalyzer(), "sv", "swedish"),
    TextAnalyzer(new TurkishAnalyzer(), "tr", "turkish"),
    TextAnalyzer(new ArabicAnalyzer(), "ar", "arabic"),
    TextAnalyzer(new SmartChineseAnalyzer(), "zh", "chinese"),
    TextAnalyzer(new JapaneseAnalyzer(), "ja", "japanese")
  )

  implicit val analyzerDecoder: Decoder[TextAnalyzer] = Decoder.decodeString.emapTry(string =>
    analyzers.find(_.names.toList.contains(string)) match {
      case Some(found) => Success(found)
      case None        => Failure(new IllegalArgumentException(s"language $string is not supported"))
    }
  )

  implicit val analyzerEncoder: Encoder[TextAnalyzer] = Encoder.encodeString.contramap(_.names.head)
}
