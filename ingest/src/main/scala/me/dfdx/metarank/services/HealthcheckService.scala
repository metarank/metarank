package me.dfdx.metarank.services

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import org.http4s.HttpRoutes

object HealthcheckService {
  val route = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok("wubba lubba dub dub")
  }
}
