package ai.metarank.fstore.transfer

import ai.metarank.fstore.transfer.StateSink.TransferResult
import cats.effect.IO

trait StateSink[S, F] {
  def sink(f: F, state: fs2.Stream[IO, S]): IO[TransferResult]
}

object StateSink {
  case class TransferResult(count: Int)
}