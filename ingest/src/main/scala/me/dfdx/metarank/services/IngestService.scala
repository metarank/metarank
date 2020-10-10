package me.dfdx.metarank.services

import cats.effect._
import io.circe.Decoder
import me.dfdx.metarank.model.Event.{ClickEvent, ConversionEvent, ItemMetadataEvent, RankEvent}
import io.circe.generic.semiauto._
import me.dfdx.metarank.model.Event
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.HttpRoutes

object IngestService {
  implicit val itemListDecoder = Decoder.decodeList(Event.itemMetadataCodec)

  implicit val itemListJson = jsonOf[IO, List[ItemMetadataEvent]]
  implicit val itemJson     = jsonOf[IO, ItemMetadataEvent]
  implicit val rankJson     = jsonOf[IO, RankEvent]
  implicit val clickJson    = jsonOf[IO, ClickEvent]
  implicit val convJson     = jsonOf[IO, ConversionEvent]

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
    case request @ POST -> Root / "ingest" / "click" =>
      for {
        item     <- request.as[ClickEvent]
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / "ingest" / "conv" =>
      for {
        item     <- request.as[ConversionEvent]
        response <- Ok()
      } yield {
        response
      }

  }
}
