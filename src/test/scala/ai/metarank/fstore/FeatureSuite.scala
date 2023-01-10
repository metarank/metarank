package ai.metarank.fstore

import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.{Feature, FeatureValue, State, Timestamp, Write}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait FeatureSuite[W <: Write, C <: FeatureConfig, F <: Feature[W, _ <: FeatureValue]]
    extends AnyFlatSpec
    with Matchers {
  lazy val now = Timestamp.date(2021, 6, 1, 0, 0, 1)
  def config: C
  def feature(): F = feature(config)
  def feature(config: C): F
  def write(values: List[W]): Option[FeatureValue] = {
    val f = feature(config)
    values.foreach(w => f.put(w).unsafeRunSync())
    values.lastOption.flatMap(last => f.computeValue(last.key, last.ts).unsafeRunSync())
  }

  def stateSource[S](f: F)(implicit s: StateSource[S, F]) = s.source(f)

  it should "be empty" in {
    write(Nil) shouldBe None
  }

}
