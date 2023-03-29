package ai.metarank.fstore.cache

import ai.metarank.fstore.TrainStore
import ai.metarank.model.TrainValues
import cats.effect.IO

case class CachedTrainStore(fast: TrainStore, slow: TrainStore) extends TrainStore {
  override def put(cts: List[TrainValues]): IO[Unit] =
    fast.put(cts) *> slow.put(cts)

  override def flush(): IO[Unit] = fast.flush() *> slow.flush()

  override def getall(): fs2.Stream[IO, TrainValues] =
    slow.getall()

}
