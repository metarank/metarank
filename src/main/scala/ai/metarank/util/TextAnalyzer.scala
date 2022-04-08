package ai.metarank.util

import io.circe.Decoder
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

case class TextAnalyzer(analyzer: Analyzer) {
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
  def analyzer1(tok: Tokenizer) = new Analyzer() {
    override def createComponents(fieldName: String): Analyzer.TokenStreamComponents =
      new TokenStreamComponents(tok)
  }
  lazy val icu        = TextAnalyzer(analyzer1(new ICUTokenizer()))
  lazy val whitespace = TextAnalyzer(analyzer1(new WhitespaceTokenizer()))

  lazy val generic    = TextAnalyzer(new StandardAnalyzer())
  lazy val english    = TextAnalyzer(new EnglishAnalyzer())
  lazy val czech      = TextAnalyzer(new CzechAnalyzer())
  lazy val danish     = TextAnalyzer(new DanishAnalyzer())
  lazy val dutch      = TextAnalyzer(new DutchAnalyzer())
  lazy val estonian   = TextAnalyzer(new EstonianAnalyzer())
  lazy val finnish    = TextAnalyzer(new FinnishAnalyzer())
  lazy val french     = TextAnalyzer(new FrenchAnalyzer())
  lazy val german     = TextAnalyzer(new GermanAnalyzer())
  lazy val greek      = TextAnalyzer(new GreekAnalyzer())
  lazy val italian    = TextAnalyzer(new ItalianAnalyzer())
  lazy val norwegian  = TextAnalyzer(new NorwegianAnalyzer())
  lazy val polish     = TextAnalyzer(new PolishAnalyzer())
  lazy val portuguese = TextAnalyzer(new PortugueseAnalyzer())
  lazy val spanish    = TextAnalyzer(new SpanishAnalyzer())
  lazy val swedish    = TextAnalyzer(new SwedishAnalyzer())
  lazy val turkish    = TextAnalyzer(new TurkishAnalyzer())
  lazy val arabic     = TextAnalyzer(new ArabicAnalyzer())
  lazy val chinese    = TextAnalyzer(new SmartChineseAnalyzer())
  lazy val japanese   = TextAnalyzer(new JapaneseAnalyzer())

  implicit val analyzerDecoder: Decoder[TextAnalyzer] = Decoder.decodeString.emapTry {
    case "generic"           => Success(generic)
    case "en" | "english"    => Success(english)
    case "cz" | "czech"      => Success(czech)
    case "da" | "danish"     => Success(danish)
    case "nl" | "dutch"      => Success(dutch)
    case "et" | "estonian"   => Success(estonian)
    case "fi" | "finnish"    => Success(finnish)
    case "fr" | "french"     => Success(french)
    case "de" | "german"     => Success(german)
    case "gr" | "greek"      => Success(greek)
    case "it" | "italian"    => Success(italian)
    case "no" | "norwegian"  => Success(norwegian)
    case "pl" | "polish"     => Success(polish)
    case "pt" | "portuguese" => Success(portuguese)
    case "es" | "spanish"    => Success(spanish)
    case "sv" | "swedish"    => Success(swedish)
    case "tr" | "turkish"    => Success(turkish)
    case "ar" | "arabic"     => Success(arabic)
    case "zh" | "chinese"    => Success(chinese)
    case "ja" | "japanese"   => Success(japanese)
    case other               => Failure(new IllegalArgumentException(s"language $other is not supported"))
  }
}
