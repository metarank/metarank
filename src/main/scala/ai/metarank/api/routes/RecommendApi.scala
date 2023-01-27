package ai.metarank.api.routes

import ai.metarank.api.JsonChunk
import ai.metarank.fstore.Persistence
import ai.metarank.ml.Recommender
import ai.metarank.ml.recommend.RecommendRequest
import ai.metarank.util.Logging
import cats.effect._
import io.circe.parser._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

case class RecommendApi(rec: Recommender, state: Persistence) extends Logging {

  val routes = HttpRoutes.of[IO] { case post @ POST -> Root / "recommend" / model =>
    for {
      json     <- post.as[String]
      request  <- IO.fromEither(decode[RecommendRequest](json))
      response <- rec.recommend(request, model)
    } yield {
      Response[IO](
        Status.Ok,
        headers = Headers(`Content-Type`(MediaType.application.json)),
        entity = Entity.strict(JsonChunk(response))
      )

    }
  }
}
