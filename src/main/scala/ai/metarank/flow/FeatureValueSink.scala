package ai.metarank.flow

import ai.metarank.fstore.Persistence
import ai.metarank.model.FeatureValue
import cats.effect.IO
import fs2.Pipe
import scala.concurrent.duration._

case class FeatureValueSink(store: Persistence) {
  def write: Pipe[IO, FeatureValue, Nothing] = values =>
    values
      .groupWithin(128, 1.second)
      .evalMap(chunk => store.values.put(chunk.toList.map(fv => fv.key -> fv).toMap))
      .drain
}
