package ai.metarank.main

import ai.metarank.feature.matcher.BM25Matcher.TermFreqDic
import ai.metarank.main.command.TermFreq
import ai.metarank.model.Event.ItemEvent
import ai.metarank.util.RanklensEvents
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TermFreqDicTest extends AnyFlatSpec with Matchers {
  it should "build termfreq from ranklens dataset" in {
    val events = RanklensEvents.stream()
    val tf     = TermFreq.processLanguage("english", Set("title", "description"), events).unsafeRunSync()
    tf.docs shouldBe 5024
    tf.termfreq.size shouldBe 11560
  }

  it should "save tf to a file" in {
    val dir = File.newTemporaryDirectory("tfdic")
    val dic = TermFreqDic(
      language = "english",
      fields = List("title"),
      docs = 100,
      avgdl = 10.0,
      termfreq = Map("foo" -> 1)
    )
    TermFreq.saveFile(dir.toString() + "/dic.json", dic).unsafeRunSync()
    dir.children.toList.size shouldBe 1
    dir.delete()
  }
}
