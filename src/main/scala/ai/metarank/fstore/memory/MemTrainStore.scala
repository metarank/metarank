package ai.metarank.fstore.memory

import ai.metarank.flow.PrintProgress
import ai.metarank.fstore.TrainStore
import ai.metarank.model.TrainValues
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemTrainStore(
    cache: Cache[String, AnyRef] = Scaffeine().build[String, AnyRef]()
) extends TrainStore {

  override def put(cts: List[TrainValues]): IO[Unit] = IO {
    cts.foreach(ct => cache.put(ct.id.value, ct))
  }

  override def flush(): IO[Unit] = IO.unit

  override def getall(): fs2.Stream[IO, TrainValues] = {
    fs2.Stream
      .fromBlockingIterator[IO](
        cache.asMap().iterator.map(_._2).flatMap(_.cast[TrainValues]),
        1000
      )
      .through(PrintProgress.tap(None, "click-throughs"))
  }
}
