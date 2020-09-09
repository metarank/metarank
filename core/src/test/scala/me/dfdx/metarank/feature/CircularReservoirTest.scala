package me.dfdx.metarank.feature

import me.dfdx.metarank.feature.TumblingWindowCountingFeature.CircularReservoir
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Random

class CircularReservoirTest extends AnyPropSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 200)

  val events: Gen[List[Int]] = for {
    length <- Gen.chooseNum[Int](1, 90)
    start  <- Gen.posNum[Int]
    list   <- Gen.listOfN(length, Gen.chooseNum[Int](0, 3)).map(_.toArray)
  } yield {
    var acc    = start
    val result = new Array[Int](length)
    var i      = 0
    while (i < length) {
      acc += list(i)
      result(i) = acc
      i += 1
    }
    result.toList
  }

  def offset(start: Int, length: Int) = start + math.round(math.sqrt(Random.nextInt(length * length)).toFloat)

  property("it should always skip the current day") {
    val updated = (0 to 10).foldLeft(CircularReservoir(10))((buf, _) => buf.increment(1))
    updated.sumLast(5) shouldBe 0
  }

  property("it should have sum of last N days") {
    forAll(events) { list =>
      val now     = list.max
      val updated = list.foldLeft(CircularReservoir(10))((buf, day) => buf.increment(day))
      updated.sumLast(1) shouldBe list.count(day => (day >= now - 1) && (day < now))
    }
  }
}
