package ai.metarank.main

import ai.metarank.feature.matcher.BM25Matcher.TermFreqDic
import ai.metarank.main.command.TermFreq
import ai.metarank.model.Event
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Field.StringField
import ai.metarank.util.{RanklensEvents, TestItemEvent}
import better.files.File
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TermFreqDicTest extends AnyFlatSpec with Matchers {
  it should "build termfreq from ranklens dataset" in {
    val events = RanklensEvents.stream()
    val tf     = TermFreq.processLanguage("english", Set("title", "description"), events).unsafeRunSync()
    tf.docs shouldBe 2512
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

  it should "count repetitions only once" in {
    val event =
      TestItemEvent("p1", List(StringField("foo", "hello hello world"), StringField("bar", "hello hello world")))
    val events = fs2.Stream(event)
    val tf     = TermFreq.processLanguage("english", Set("foo", "bar"), events).unsafeRunSync()
    tf shouldBe TermFreqDic(
      language = "en",
      fields = List("bar", "foo"),
      docs = 1,
      avgdl = 3.0,
      termfreq = Map("hello" -> 1, "world" -> 1)
    )
  }
}
