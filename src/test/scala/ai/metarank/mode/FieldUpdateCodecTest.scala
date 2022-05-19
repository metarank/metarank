package ai.metarank.mode

import ai.metarank.mode.bootstrap.FieldUpdateCodec
import ai.metarank.model.Field.{BooleanField, NumberField, NumberListField, StringField, StringListField}
import ai.metarank.model.{FieldId, FieldUpdate}
import ai.metarank.model.FieldId.{ItemFieldId, UserFieldId}
import ai.metarank.model.Identifier.{ItemId, UserId}
import io.findify.featury.model.Key.Tenant
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.io.{BufferedInputStream, ByteArrayInputStream}

class FieldUpdateCodecTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  it should "not fail on EOF" in {
    val empty = new ByteArrayInputStream(new Array[Byte](0))
    FieldUpdateCodec.read(empty) shouldBe None
  }
  val tenantGen = Gen.chooseNum(0, 1000).map(num => Tenant(s"tenant$num"))
  val itemGen   = Gen.chooseNum(0, 1000).map(num => ItemId(s"item$num"))
  val userGen   = Gen.chooseNum(0, 1000).map(num => UserId(s"user$num"))

  val itemIdGen = for {
    tenant <- tenantGen
    item   <- itemGen
    value  <- Gen.numStr
  } yield {
    ItemFieldId(tenant, item, value)
  }
  val userIdGen = for {
    tenant <- tenantGen
    user   <- userGen
    value  <- Gen.numStr
  } yield {
    UserFieldId(tenant, user, value)
  }
  val idGen = Gen.oneOf[FieldId](itemIdGen, userIdGen)

  val stringFieldGen = for {
    name  <- Gen.asciiStr
    value <- Gen.asciiStr
  } yield {
    StringField(name, value)
  }

  val boolFieldGen = for {
    name  <- Gen.asciiStr
    value <- Gen.oneOf(true, false)
  } yield {
    BooleanField(name, value)
  }

  val numFieldGen = for {
    name  <- Gen.asciiStr
    value <- Gen.posNum[Double]
  } yield {
    NumberField(name, value)
  }

  val stringListFieldGen = for {
    name  <- Gen.asciiStr
    value <- Gen.listOf(Gen.asciiStr)
  } yield {
    StringListField(name, value)
  }

  val numberListFieldGen = for {
    name  <- Gen.asciiStr
    value <- Gen.listOf(Gen.posNum[Double])
  } yield {
    NumberListField(name, value)
  }

  val fieldGen = Gen.oneOf(stringFieldGen, numFieldGen, boolFieldGen, stringListFieldGen, numberListFieldGen)
  val fieldUpdateGen = for {
    id    <- idGen
    value <- fieldGen
  } yield {
    FieldUpdate(id, value)
  }

  it should "process events" in {
    forAll(fieldUpdateGen) { update =>
      {
        val out = new ByteArrayOutputStream()
        FieldUpdateCodec.write(update, out)
        val decoded = FieldUpdateCodec.read(new BufferedInputStream(new ByteArrayInputStream(out.toByteArray), 10))
        decoded shouldBe Some(update)
      }
    }
  }
}
