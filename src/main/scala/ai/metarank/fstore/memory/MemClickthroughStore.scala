package ai.metarank.fstore.memory

import ai.metarank.flow.PrintProgress
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.model.ClickthroughValues
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemClickthroughStore(
    cache: Cache[String, AnyRef] = Scaffeine().build[String, AnyRef]()
) extends ClickthroughStore {

  override def put(cts: List[ClickthroughValues]): IO[Unit] = IO {
    cts.foreach(ct => cache.put(ct.ct.id.value, ct))
  }

  override def flush(): IO[Unit] = IO.unit

  override def getall(): fs2.Stream[IO, ClickthroughValues] = {
    fs2.Stream
      .fromBlockingIterator[IO](
        cache.asMap().iterator.map(_._2).flatMap(_.cast[ClickthroughValues]),
        1000
      )
      .through(PrintProgress.tap(None, "click-throughs"))
  }
}
