package ai.metarank.util

import ai.metarank.model.Event.{RankItem, RankingEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, UserId}
import ai.metarank.model.{EventId, Timestamp}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

class RankingEventFormatTest extends AnyFlatSpec with Matchers {
  it should "roundtrip" in {
    val event = RankingEvent(
      id = EventId("a"),
      timestamp = Timestamp.now,
      user = Some(UserId("u")),
      session = None,
      fields = List(StringField("foo", "bar"), StringListField("baz", List("qux")), NumberField("a", 1)),
      items = NonEmptyList.of(RankItem(id = ItemId("i1"), fields = List(NumberField("rel", 2))))
    )
    val buffer = new ByteArrayOutputStream()
    val out    = new DataOutputStream(buffer)
    RankingEventFormat.write(event, out)

    val in      = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray))
    val decoded = RankingEventFormat.read(in)
    decoded shouldBe event
  }
}
