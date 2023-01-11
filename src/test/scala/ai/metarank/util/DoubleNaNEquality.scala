package ai.metarank.util

import org.scalactic.{Equality, TolerantNumerics}

object DoubleNaNEquality {
  implicit def doubleEqWithNaNAndTol: Equality[Double] = new Equality[Double] {

    implicit val tolerance: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(1e-6)

    def areEqual(expected: Double, actual: Any): Boolean = actual match {
      case x: Double if x.isNaN => expected.isNaN
      case x: Double            => tolerance.areEqual(expected, x)
      case _                    => false
    }
  }
}
