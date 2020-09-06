package me.dfdx.metarank.services

import cats.effect.IO
import me.dfdx.metarank.model.{RankRequest, RankResponse}
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.io._

object RankService {
  implicit val requestJson  = jsonOf[IO, RankRequest]
  implicit val responseJson = jsonEncoderOf[IO, RankResponse]

  val route = HttpRoutes.of[IO] {
    case request @ POST -> Root / "rank" =>
      for {
        rankRequest <- request.as[RankRequest]
        response    <- Ok(RankResponse(rankRequest.items))
      } yield {
        response
      }

  }
}
