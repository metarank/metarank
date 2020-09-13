package me.dfdx.metarank.aggregation

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class CountAggregationTest extends AnyPropSpec with Matchers with ScalaCheckDrivenPropertyChecks {

//  property("should ser-de itself") {
//    forAll(Gen.asciiPrintableStr) { str =>
//      {
//        val feature = TumblingWindowCountingFeature(3, 10, str)
//        val buffer  = new ByteArrayOutputStream()
//        feature.write(new DataOutputStream(buffer))
//        val read = TumblingWindowCountingFeature.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray)))
//        feature shouldBe read
//      }
//    }
//  }
}
