package ai.metarank.fstore.transfer

import cats.effect.IO
import fs2.Stream

trait StateSource[S, F] {
  def source(f: F): Stream[IO, S]
}
