package ai.metarank.source

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelKey
import ai.metarank.model.Env
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
import cats.effect.IO
import scala.concurrent.duration._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class ModelCache(
    state: Persistence,
    cache: Cache[ModelKey, Scorer] = Scaffeine().expireAfterWrite(5.minutes).build[ModelKey, Scorer]()
) extends Logging {
  def get(env: Env, name: String): IO[Scorer] = for {
    scorerOption <- IO(cache.getIfPresent(ModelKey(env, name)))
    scorer <- scorerOption match {
      case Some(cached) => IO.pure(cached)
      case None =>
        for {
          start <- IO(System.currentTimeMillis())
          _     <- info(s"loading model ${env.value}.$name from store")
          key = ModelKey(env, name)
          loadedOption <- state.models.get(List(key)).map(_.get(key))
          loaded <- loadedOption match {
            case Some(model) =>
              info(s"model ${env.value}.$name loaded in ${System.currentTimeMillis() - start}ms") *> IO.pure(model)
            case None =>
              warn(s"model $name not found") *> IO.raiseError(
                new Exception(s"model ${env.value}.$name is missing in store")
              )
          }
          _ <- IO(cache.put(key, loaded))
        } yield {
          loaded
        }
    }
  } yield {
    scorer
  }
}
