package ai.metarank.mode.inference

import cats.effect.{IO, Ref, Resource}
import io.findify.featury.values.FeatureStore

case class FeatureStoreResource(storeRef: Ref[IO, FeatureStore], makeStore: () => FeatureStore) {
  def reconnect(): IO[FeatureStoreResource] = for {
    ref <- Ref.of[IO, FeatureStore](makeStore())
  } yield {
    FeatureStoreResource(ref, makeStore)
  }
}

object FeatureStoreResource {
  def make(makeStore: () => FeatureStore): Resource[IO, FeatureStoreResource] = {
    Resource.make(for {
      ref <- Ref.of[IO, FeatureStore](makeStore())
    } yield FeatureStoreResource(ref, makeStore))(_.storeRef.get.flatMap(_.close()))
  }

  def unsafe(makeStore: () => FeatureStore): IO[Ref[IO, FeatureStoreResource]] = for {
    ref <- Ref.of[IO, FeatureStore](makeStore())
    res <- Ref.of[IO, FeatureStoreResource](FeatureStoreResource(ref, makeStore))
  } yield {
    res
  }
}
