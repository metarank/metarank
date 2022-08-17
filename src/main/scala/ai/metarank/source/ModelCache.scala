package ai.metarank.source

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
import cats.effect.IO
import scala.concurrent.duration._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

trait ModelCache {
  def get(name: String): IO[Scorer]
}

object ModelCache {
  case class MemoryModelCache(
      state: Persistence,
      cache: Cache[String, Scorer] = Scaffeine().expireAfterWrite(5.minutes).build[String, Scorer]()
  ) extends ModelCache
      with Logging {
    def get(name: String): IO[Scorer] = for {
      scorerOption <- IO(cache.getIfPresent(name))
      scorer <- scorerOption match {
        case Some(cached) => IO.pure(cached)
        case None =>
          for {
            start <- IO(System.currentTimeMillis())
            _     <- info(s"loading model $name from store")
            key = ModelName(name)
            loadedOption <- state.models.get(List(key)).map(_.get(key))
            loaded <- loadedOption match {
              case Some(model) =>
                info(s"model $name loaded in ${System.currentTimeMillis() - start}ms") *> IO.pure(model)
              case None =>
                warn(s"model $name not found") *> IO.raiseError(
                  new Exception(s"model $name is missing in store")
                )
            }
            _ <- IO(cache.put(key.name, loaded))
          } yield {
            loaded
          }
      }
    } yield {
      scorer
    }
  }

}
