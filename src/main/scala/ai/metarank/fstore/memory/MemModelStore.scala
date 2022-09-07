package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.{KVStore, ModelName}
import ai.metarank.rank.Model.Scorer
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemModelStore(cache: Cache[ModelName, AnyRef] = Scaffeine().build()) extends KVStore[ModelName, Scorer] {
  override def get(keys: List[ModelName]): IO[Map[ModelName, Scorer]] =
    IO(keys.flatMap(key => cache.getIfPresent(key).flatMap(_.cast[Scorer]).map(v => key -> v)).toMap)

  override def put(values: Map[ModelName, Scorer]): IO[Unit] = IO {
    values.foreach { case (k, v) => cache.put(k, v) }
  }

}
