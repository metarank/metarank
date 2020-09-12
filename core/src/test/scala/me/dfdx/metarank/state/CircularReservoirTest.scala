package me.dfdx.metarank.state

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}

import me.dfdx.metarank.feature.generator.IncrementingTimestampsGenerator
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.tracker.state.CircularReservoir
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

  property("it should have sum of last N days") {
    forAll(events) { list =>
      val now     = list.max
      val updated = list.foldLeft(CircularReservoir(10))((buf, day) => buf.increment(Timestamp.day(day)))
      updated.sumLast(1) shouldBe list.count(day => (day >= now - 1) && (day < now))
    }
  }

  property("it should save-load itself") {
    forAll(events) { list =>
      val updated = list.foldLeft(CircularReservoir(10))((buf, day) => buf.increment(Timestamp.day(day)))
      val buffer  = new ByteArrayOutputStream()
      CircularReservoir.ctWriter.write(updated, new DataOutputStream(buffer))
      val read = CircularReservoir.crReader.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray)))
      updated shouldBe read
    }
  }
}
