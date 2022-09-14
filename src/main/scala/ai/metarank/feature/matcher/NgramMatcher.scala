package ai.metarank.feature.matcher

import ai.metarank.util.TextAnalyzer
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder}

import scala.collection.mutable.ArrayBuffer

case class NgramMatcher(n: Int, language: TextAnalyzer) extends FieldMatcher {
  override def tokenize(string: String): Array[String] = {
    val terms = language.split(string)
    if (terms.length == 0) Array.empty[String]
    else {
      val ngramsBuffer = new ArrayBuffer[String](terms.length)
      var i            = 0
      while (i < terms.length) {
        val term = terms(i)
        var j    = 0
        while (j <= term.length - n) {
          val ngram = term.substring(j, j + n)
          ngramsBuffer.append(ngram)
          j += 1
        }
        i += 1
      }
      unique(ngramsBuffer.toArray)
    }
  }

}

object NgramMatcher {
  implicit val ngramDecoder: Decoder[NgramMatcher] = deriveDecoder
  implicit val ngramEncoder: Encoder[NgramMatcher] = deriveEncoder
}
