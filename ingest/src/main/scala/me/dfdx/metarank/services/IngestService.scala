package me.dfdx.metarank.services

import cats.effect._
import io.circe.Decoder
import me.dfdx.metarank.model.Event.{InteractionEvent, ItemMetadataEvent, RankEvent}
import io.circe.generic.semiauto._
import me.dfdx.metarank.model.Event
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.HttpRoutes

object IngestService {
  implicit val itemListDecoder = Decoder.decodeList(Event.itemMetadataCodec)

  implicit val itemListJson    = jsonOf[IO, List[ItemMetadataEvent]]
  implicit val itemJson        = jsonOf[IO, ItemMetadataEvent]
  implicit val rankJson        = jsonOf[IO, RankEvent]
  implicit val interactionJson = jsonOf[IO, InteractionEvent]

  val route = HttpRoutes.of[IO] {
    case request @ POST -> Root / "ingest" / "item" / "batch" =>
      for {
        items    <- request.as[List[ItemMetadataEvent]]
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / "ingest" / "item" =>
      for {
        item     <- request.as[ItemMetadataEvent]
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / "ingest" / "rank" =>
      for {
        item     <- request.as[RankEvent]
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / "ingest" / "interaction" =>
      for {
        item     <- request.as[InteractionEvent]
        response <- Ok()
      } yield {
        response
      }

  }
}
