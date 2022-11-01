package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.ClickthroughStore
import ai.metarank.model.ClickthroughValues
import cats.effect.IO

object DiscardClickthroughStore extends ClickthroughStore {
  override def flush(): IO[Unit] = IO.unit

  override def getall(): fs2.Stream[IO, ClickthroughValues] = fs2.Stream.empty

  override def put(cts: List[ClickthroughValues]): IO[Unit] = IO.unit
}
