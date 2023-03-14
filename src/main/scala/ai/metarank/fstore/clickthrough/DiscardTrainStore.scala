package ai.metarank.fstore.clickthrough

import ai.metarank.fstore.TrainStore
import ai.metarank.model.TrainValues
import cats.effect.IO

object DiscardTrainStore extends TrainStore {
  override def getall(): fs2.Stream[IO, TrainValues] = fs2.Stream.empty

  override def flush(): IO[Unit]                     = IO.unit
  override def put(cts: List[TrainValues]): IO[Unit] = IO.unit
}
