package me.dfdx.metarank.services

import cats.effect._
import cats.implicits._
import io.circe.Decoder
import me.dfdx.metarank.model.Event.{ClickEvent, ConversionEvent, ItemMetadataEvent, RankEvent}
import io.circe.generic.semiauto._
import me.dfdx.metarank.Aggregations
import me.dfdx.metarank.model.{Event, Featurespace}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.HttpRoutes

case class IngestService(fs: Featurespace, aggs: Aggregations) {
  implicit val itemListDecoder = Decoder.decodeList(Event.itemMetadataCodec)

  implicit val itemListJson = jsonOf[IO, List[ItemMetadataEvent]]
  implicit val itemJson     = jsonOf[IO, ItemMetadataEvent]
  implicit val rankJson     = jsonOf[IO, RankEvent]
  implicit val clickJson    = jsonOf[IO, ClickEvent]
  implicit val convJson     = jsonOf[IO, ConversionEvent]

  val route = HttpRoutes.of[IO] {
    case request @ POST -> Root / fs.name / "ingest" / "item" / "batch" =>
      for {
        items    <- request.as[List[ItemMetadataEvent]]
        _        <- items.map(aggs.onEvent).sequence
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / fs.name / "ingest" / "item" =>
      for {
        item     <- request.as[ItemMetadataEvent]
        _        <- aggs.onEvent(item)
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / fs.name / "ingest" / "rank" =>
      for {
        item     <- request.as[RankEvent]
        _        <- aggs.onEvent(item)
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / fs.name / "ingest" / "click" =>
      for {
        item     <- request.as[ClickEvent]
        _        <- aggs.onEvent(item)
        response <- Ok()
      } yield {
        response
      }
    case request @ POST -> Root / fs.name / "ingest" / "conv" =>
      for {
        item     <- request.as[ConversionEvent]
        _        <- aggs.onEvent(item)
        response <- Ok()
      } yield {
        response
      }

  }
}
