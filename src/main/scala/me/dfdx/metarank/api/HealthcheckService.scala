package me.dfdx.metarank.api

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

object HealthcheckService {
  val route = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok("wubba lubba dub dub")
  }
}
