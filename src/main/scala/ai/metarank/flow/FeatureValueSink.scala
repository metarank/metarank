package ai.metarank.flow

import ai.metarank.fstore.Persistence
import ai.metarank.model.FeatureValue
import cats.effect.IO
import fs2.Pipe
import scala.concurrent.duration._

case class FeatureValueSink(store: Persistence) {
  def write: Pipe[IO, List[FeatureValue], Nothing] = values =>
    values
      .evalMap(chunk => store.values.put(chunk.map(fv => fv.key -> fv).toMap))
      .drain
}
