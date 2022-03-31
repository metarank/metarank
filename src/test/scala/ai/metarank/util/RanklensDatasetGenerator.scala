package ai.metarank.util

import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, MetadataEvent, RankingEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model._
import better.files.File
import cats.data.NonEmptyList
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.{Codec, Decoder}
import io.findify.featury.model.Timestamp
import io.circe.syntax._
import io.findify.featury.model.Key.Tenant

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.duration._

/** Converter of Ranklens dataset (https://github.com/metarank/ranklens/) into metarank-compatible event schema with
  * meta/impression/interaction events
  */

object RanklensDatasetGenerator {
  case class RankLens(movies: List[Movie], actions: List[Task])
  case class Movie(
      id: Int,
      tmdbId: Int,
      title: String,
      budget: Long,
      genres: List[Genre],
      overview: String,
      tmdbPopularity: Double,
      tmdbVoteCount: Long,
      tmdbVoteAverage: Double,
      releaseDate: String,
      revenue: Double,
      runtime: Int,
      topActors: List[Cast],
      director: Option[Cast],
      writer: Option[Cast],
      tags: List[String]
  )
  case class Cast(id: Int, name: String, gender: Int, popularity: Double)
  case class Genre(id: Int, name: String)
  case class Task(ts: String, id: Int, user: String, shown: List[Int], liked: List[Int])

  implicit val taskCodec: Codec[Task]   = deriveCodec
  implicit val genreCodec: Codec[Genre] = deriveCodec
  implicit val castCodec: Codec[Cast]   = deriveCodec
  implicit val movieCodec: Codec[Movie] = deriveCodec

  def read(dir: File): RankLens = {
    val movies  = parseJSONL[Movie](dir / "metadata.jsonl")
    val actions = parseJSONL[Task](dir / "ranking.jsonl")
    RankLens(movies, actions)
  }

  private def parseJSONL[T: Decoder](file: File): List[T] = {
    file.lineIterator
      .flatMap(line => {
        decode[T](line) match {
          case Left(err) =>
            println(s"cannot parse: $err")
            None
          case Right(value) => Some(value)
        }
      })
      .toList
  }

  def main(args: Array[String]): Unit = {
    val list = events(args.head)
    File("/tmp/events.jsonl").write(list.map(_.asJson.noSpacesSortKeys).mkString("\n"))
  }

  def events(path: String): List[Event] = {
    val ranklens = read(File(path))
    val time0    = Timestamp.date(2021, 11, 14, 16, 25, 0)
    val meta = ranklens.movies.map(m => {
      MetadataEvent(
        id = EventId(UUID.randomUUID().toString),
        item = ItemId(m.id.toString),
        timestamp = time0,
        fields = List.concat(
          List(
            StringField("title", m.title),
            NumberField("popularity", m.tmdbPopularity),
            NumberField("vote_avg", m.tmdbVoteAverage),
            NumberField("vote_cnt", m.tmdbVoteCount),
            NumberField("budget", m.budget),
            NumberField("runtime", m.runtime),
            NumberField(
              "release_date",
              LocalDate.parse(m.releaseDate, DateTimeFormatter.ISO_DATE).atTime(0, 0, 0).toEpochSecond(ZoneOffset.UTC)
            ),
            StringListField("genres", m.genres.map(_.name.toLowerCase())),
            StringListField("tags", m.tags),
            StringListField("actors", m.topActors.map(_.name.toLowerCase))
          ),
          m.director.map(d => StringField("director", d.name.toLowerCase())),
          m.writer.map(w => StringField("writer", w.name.toLowerCase()))
        ),
        tenant = "default"
      )
    })
    val actions: List[Event] = ranklens.actions.flatMap(t => {
      val id        = EventId(UUID.randomUUID().toString)
      val eventTime = LocalDateTime.parse(t.ts, DateTimeFormatter.ISO_DATE_TIME)
      val ts = Timestamp.date(
        eventTime.getYear,
        eventTime.getMonth.getValue,
        eventTime.getDayOfMonth,
        eventTime.getHour,
        eventTime.getMinute,
        eventTime.getSecond
      )
      val impression = List(
        RankingEvent(
          id = id,
          timestamp = ts,
          user = UserId(t.user),
          session = SessionId(t.user),
          fields = Nil,
          items = NonEmptyList.fromListUnsafe(t.shown).map(id => ItemRelevancy(ItemId(id.toString), 0)),
          tenant = "default"
        )
      )
      val clicks = t.liked.map(item =>
        InteractionEvent(
          id = EventId(UUID.randomUUID().toString),
          timestamp = ts.plus(5.second),
          user = UserId(t.user),
          session = SessionId(t.user),
          fields = Nil,
          item = ItemId(item.toString),
          ranking = id,
          `type` = "click",
          tenant = "default"
        )
      )
      impression ++ clicks
    })
    meta ++ actions
  }
}
