package ai.metarank.tool

import better.files.Resource
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.zip.GZIPInputStream

class MovielensRatingsSourceTest extends AnyFlatSpec with Matchers {
  it should "pull things from a file" in {
    val stream = new GZIPInputStream(Resource.my.getAsStream("/movielens/1m/ratings.dat.gz"))
    val events =
      MovielensRatingsSource
        .fromInputStream(stream)
        .take(1000)
        .compile
        .count
        .unsafeRunSync()
    events shouldBe 1000
  }
}
