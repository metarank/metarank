package ai.metarank.mode.inference.api

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import org.http4s.circe._

object HealthApi {
  val routes = HttpRoutes.of[IO] { case GET -> Root / "health" =>
    Ok("")
  }
}
