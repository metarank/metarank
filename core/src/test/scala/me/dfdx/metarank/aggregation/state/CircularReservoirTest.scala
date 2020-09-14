package me.dfdx.metarank.aggregation.state

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import me.dfdx.metarank.aggregation.CircularReservoir
import me.dfdx.metarank.aggregation.generator.IncrementingTimestampsGenerator
import me.dfdx.metarank.model.Timestamp
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Random

class CircularReservoirTest extends AnyPropSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 200, minSize = 1)

  val events = IncrementingTimestampsGenerator()

  def offset(start: Int, length: Int) = start + math.round(math.sqrt(Random.nextInt(length * length)).toFloat)

  property("it should always skip the current day") {
    val updated = (0 to 10).foldLeft(CircularReservoir(10))((buf, _) => buf.increment(Timestamp.day(1)))
    updated.sumLast(5) shouldBe 0
  }

  property("it should work with large windows over small buffer") {
    val buf = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))
    buf.sumLast(100) shouldBe 1
  }

  property("it should not overflow") {
    val buf = CircularReservoir(3)
      .increment(Timestamp.day(1))
      .increment(Timestamp.day(2))
      .increment(Timestamp.day(3))
      .increment(Timestamp.day(4))
    buf.sumLast(10) shouldBe 2
  }

  property("it should not overflow v2") {
    val buf = CircularReservoir(10)
      .increment(Timestamp.day(6))
      .increment(Timestamp.day(6))
      .increment(Timestamp.day(8))
      .increment(Timestamp.day(8))
      .increment(Timestamp.day(8))
    buf.sumLast(1) shouldBe 0
  }

  property("it should not overflow v3") {
    val buf = CircularReservoir(10)
      .increment(Timestamp.day(5))
      .increment(Timestamp.day(6))
    buf.sumLast(1) shouldBe 1
  }

  property("it should have sum of last 1 days") {
    forAll(events) { list =>
      if (list.nonEmpty && list.forall(_ >= 0)) {
        val now      = list.max
        val updated  = list.foldLeft(CircularReservoir(10))((buf, day) => buf.increment(Timestamp.day(day)))
        val computed = updated.sumLast(1)
        val expected = list.count(day => (day >= now - 1) && (day < now))
        computed shouldBe expected
      }
    }
  }

  property("it should save-load itself") {
    forAll(events) { list =>
      val updated = list.foldLeft(CircularReservoir(10))((buf, day) => buf.increment(Timestamp.day(day)))
      val buffer  = new ByteArrayOutputStream()
      CircularReservoir.ctReaderWriter.write(updated, new DataOutputStream(buffer))
      val read =
        CircularReservoir.ctReaderWriter.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray)))
      updated shouldBe read
    }
  }
}
