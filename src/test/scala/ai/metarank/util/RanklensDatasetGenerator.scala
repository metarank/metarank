package ai.metarank.util

import ai.metarank.model.Event.{RankingEvent, InteractionEvent, ItemRelevancy, MetadataEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model._
import better.files.File
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.{Codec, Decoder}
import io.findify.featury.model.Timestamp
import io.circe.syntax._
import java.util.UUID
import scala.concurrent.duration._

/** Converter of Ranklens dataset (https://github.com/metarank/ranklens/)
  * into metarank-compatible event schema with meta/impression/interaction events
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
      releaseDate: Long,
      revenue: Double,
      runtime: Int,
      topActors: List[Cast],
      director: Option[Cast],
      writer: Option[Cast],
      tags: List[String]
  )
  case class Cast(id: Int, name: String, gender: Int, popularity: Double)
  case class Genre(id: Int, name: String)
  case class Task(ts: Long, id: Int, user: String, shown: List[Int], liked: List[Int])

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
      .map(line => decode[T](line))
      .foldLeft(List.empty[T])((acc, result) =>
        result match {
          case Left(err) =>
            println(s"cannot parse: $err")
            acc
          case Right(value) => value +: acc
        }
      )
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
        fields = List(
          StringField("title", m.title),
          NumberField("popularity", m.tmdbPopularity),
          NumberField("vote_avg", m.tmdbVoteAverage),
          NumberField("vote_cnt", m.tmdbVoteCount),
          NumberField("budget", m.budget),
          StringListField("genres", m.genres.map(_.name.toLowerCase())),
          StringListField("tags", m.tags),
          StringListField("actors", m.topActors.map(_.name.toLowerCase))
        )
      )
    })
    val actions: List[Event] = ranklens.actions.flatMap(t => {
      val id = EventId(UUID.randomUUID().toString)
      val impression = List(
        RankingEvent(
          id = id,
          timestamp = Timestamp(t.ts),
          user = UserId(t.user),
          session = SessionId(t.user),
          fields = Nil,
          items = t.shown.map(id => ItemRelevancy(ItemId(id.toString), 0)),
          tenant = Some("1")
        )
      )
      val clicks = t.liked.map(item =>
        InteractionEvent(
          id = EventId(UUID.randomUUID().toString),
          timestamp = Timestamp(t.ts).plus(1.second),
          user = UserId(t.user),
          session = SessionId(t.user),
          fields = Nil,
          item = ItemId(item.toString),
          ranking = id,
          `type` = "click",
          tenant = Some("1")
        )
      )
      impression ++ clicks
    })
    meta ++ actions
  }
}
