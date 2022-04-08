package ai.metarank.feature.matcher

import ai.metarank.util.TextAnalyzer
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure}

case class TermMatcher(language: TextAnalyzer) extends FieldMatcher {
  override def tokenize(string: String): Array[String] = {
    val terms = language.split(string)
    if (terms.length == 0) Array.empty[String] else unique(terms)
  }
}

object TermMatcher {
  implicit val ngramDecoder: Decoder[TermMatcher] = deriveDecoder
}
