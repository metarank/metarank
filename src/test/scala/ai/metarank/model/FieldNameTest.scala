package ai.metarank.model

import ai.metarank.model.FieldName.{Interaction, Item, Ranking}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import io.circe.parser._

class FieldNameTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {
  val positive = Table(
    ("field", "parsed"),
    ("metadata.foo", FieldName(Item, "foo")),
    ("ranking.foo", FieldName(Ranking, "foo")),
    ("interaction:click.foo", FieldName(Interaction("click"), "foo"))
  )

  val negative = Table(
    "field",
    "metadata.",
    "foo",
    "..",
    "interaction:.foo",
    "",
    "ranking.foo bar"
  )

  it should "parse well-formatted field names" in {
    forAll(positive) { (field: String, parsed: FieldName) =>
      decode[FieldName](s""""$field"""") shouldBe Right(parsed)
    }
  }

  it should "fail on broken input" in {
    forAll(negative) { (field: String) =>
      decode[FieldName](s""""$field"""") shouldBe a[Left[_, _]]
    }
  }
}
