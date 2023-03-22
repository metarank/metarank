package ai.metarank.feature.matcher

import ai.metarank.feature.matcher.BM25Matcher.TermFreqDic
import ai.metarank.util.TextAnalyzer
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.apache.commons.io.IOUtils

import java.io.{File, FileReader}
import io.circe.parser._

case class BM25Matcher(language: TextAnalyzer, freq: TermFreqDic) extends FieldMatcher {
  lazy val term                                        = TermMatcher(language)
  val K1                                               = 1.2  // as in Lucene
  val B                                                = 0.75 // as in Lucene
  override def tokenize(string: String): Array[String] = term.tokenize(string)

  override def score(query: Array[String], doc: Array[String]): Double = {
    var sum     = 0.0
    var i       = 0
    val docFreq = doc.groupMapReduce(identity)(_ => 1)(_ + _) // is it a hot spot?
    while (i < query.length) {
      val term           = query(i)
      val docTermFreq    = docFreq.getOrElse(term, 0)
      val globalTermFreq = freq.docfreq.getOrElse(term, 0)
      val termIDF        = math.log(1.0 + (freq.docs - globalTermFreq + 0.5) / (globalTermFreq + 0.5))
      sum += termIDF * (docTermFreq * (K1 + 1.0)) / (docTermFreq + K1 * (1.0 - B + B * (doc.length / freq.avgdl)))
      i += 1
    }
    sum

  }

}

object BM25Matcher {
  case class TermFreqDic(docs: Int, avgdl: Double, docfreq: Map[String, Int])

  object TermFreqDic {
    case class Builder(docs: Int = 0, lenSum: Long = 0L, docFreq: Map[String, Int] = Map()) {
      def withString(terms: Array[String]): Builder = {
        Builder(
          docs = docs + 1,
          lenSum = lenSum + terms.length,
          docFreq = terms.foldLeft(docFreq)((acc, next) => {
            val cnt = acc.getOrElse(next, 0)
            acc.updated(next, cnt + 1)
          })
        )
      }
    }
    def fromItemFields(fieldsSource: fs2.Stream[IO, String], language: TextAnalyzer): IO[TermFreqDic] = {
      fieldsSource.compile
        .fold(Builder())((acc, next) => acc.withString(language.split(next)))
        .map(builder =>
          TermFreqDic(
            docs = builder.docs,
            avgdl = builder.lenSum.toDouble / builder.docs,
            docfreq = builder.docFreq
          )
        )
    }

    def fromFile(path: String): IO[TermFreqDic] = for {
      text    <- IO.blocking(IOUtils.toString(new FileReader(new File(path))))
      decoded <- IO.fromEither(decode[TermFreqDic](text))
    } yield {
      decoded
    }
  }
  implicit val termDicEncoder: Encoder[TermFreqDic] = deriveEncoder[TermFreqDic]
  implicit val termDicDecoder: Decoder[TermFreqDic] = deriveDecoder[TermFreqDic]

}
