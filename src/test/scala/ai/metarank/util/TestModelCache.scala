package ai.metarank.util

import ai.metarank.rank.Model.Scorer
import ai.metarank.source.ModelCache
import cats.effect.IO

case class TestModelCache(scorer: Scorer) extends ModelCache {
  override def get(name: String): IO[Scorer] = IO.pure(scorer)

  override def invalidate(name: String): IO[Unit] = IO.unit
}
