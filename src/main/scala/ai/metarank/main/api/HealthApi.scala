package ai.metarank.main.api

import ai.metarank.fstore.Persistence
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

case class HealthApi(persistence: Persistence) {
  val routes = HttpRoutes.of[IO] { case GET -> Root / "health" => Ok(persistence.healthcheck()) }
}
