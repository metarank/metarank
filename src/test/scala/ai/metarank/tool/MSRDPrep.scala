package ai.metarank.tool

import ai.metarank.model.{Event, EventId, Timestamp}
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, RankingEvent, UserEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, UserId}
import ai.metarank.tool.MSRDPrep.Query.Row
import better.files.File
import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import com.opencsv.{CSVParserBuilder, CSVReaderBuilder}
import io.circe.syntax._
import java.io.{FileReader, InputStreamReader}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/** A MSRD dataset converter into the Metarank format.
  */
object MSRDPrep extends IOApp {
  case class Movie(
      id: Int,
      title: String,
      overview: String,
      tags: List[String],
      genres: List[String],
      director: String,
      actors: List[String],
      characters: List[String],
      year: Int,
      votes: Long,
      rating: Double,
      popularity: Double,
      budget: Long,
      poster: String
  )

  object Movie {
    def apply(line: Array[String]) =
      new Movie(
        id = line(0).toInt,
        title = line(1),
        overview = line(2),
        tags = line(3).split(",").toList,
        genres = line(4).split(",").toList,
        director = line(5),
        actors = line(6).split(",").toList,
        characters = line(7).split(",").toList,
        year = line(8).toInt,
        votes = line(9).toLong,
        rating = line(10).toDouble,
        popularity = line(11).toDouble,
        budget = line(12).toLong,
        poster = line(13)
      )
  }

  case class QueryRow(
      query: String,
      platform: String,
      columns: Int,
      movie: Int,
      bm25: Double,
      user: String,
      timestamp: Long,
      label: Boolean
  )

  case class Query(query: String, platform: String, columns: Int, user: String, timestamp: Long, movies: List[Row])

  object Query {
    case class Row(position: Int, movie: Int, bm25: Double, label: Boolean)
  }

  object QueryRow {
    def apply(row: Array[String]) = new QueryRow(
      query = row(0),
      platform = row(1),
      columns = row(2).toInt,
      movie = row(3).toInt,
      bm25 = row(4).toDouble,
      user = row(5),
      timestamp = row(6).toLong,
      label = if (row(7) == "0") false else true
    )
  }

  override def run(args: List[String]): IO[ExitCode] = args match {
    case dirPath :: Nil =>
      IO {
        val dir    = File(dirPath)
        val events = convert(dir / "movies.csv.gz", dir / "queries.csv.gz")
        val json   = events.map(_.asJson.noSpaces).mkString("\n")
        File("/tmp/msrd.jsonl").writeText(json)
        ExitCode.Success
      }
    case _ => IO.raiseError(new Exception("usage: msrdprep <dir>"))
  }

  def convert(moviesFile: File, queriesFile: File): List[Event] = {
    val movies = readcsv(moviesFile, 1).map(Movie.apply)
    val queries =
      readcsv(queriesFile, 1).toList
        .map(QueryRow.apply)
        .groupBy(_.query)
        .map {
          case (_, list @ head :: _) =>
            Query(
              head.query,
              head.platform,
              head.columns,
              head.user,
              head.timestamp,
              list.sortBy(-_.bm25).zipWithIndex.map(qr => Row(qr._2, qr._1.movie, qr._1.bm25, qr._1.label))
            )
          case _ => ???
        }
    val seenMovies = queries.flatMap(_.movies.map(_.movie)).toSet
    val itemEvents = movies
      .filter(m => seenMovies.contains(m.id))
      .map(m =>
        ItemEvent(
          id = EventId.randomUUID,
          item = ItemId(m.id.toString),
          timestamp = Timestamp.date(2022, 11, 1, 0, 0, 1),
          fields = List(
            StringField("title", m.title),
            StringField("overview", m.overview),
            StringListField("tags", m.tags),
            StringListField("genres", m.genres),
            StringField("director", m.director),
            StringListField("actors", m.actors),
            StringListField("characters", m.characters),
            NumberField("year", m.year),
            NumberField("vote_cnt", m.votes.toDouble),
            NumberField("vote_avg", m.rating),
            NumberField("popularity", m.popularity),
            NumberField("budget", m.budget.toDouble)
          )
        )
      )

    val userEvents = queries.groupMapReduce(_.user)(identity)((a, b) => if (a.timestamp < b.timestamp) a else b).map {
      case (id, first) =>
        UserEvent(
          id = EventId.randomUUID,
          user = UserId(id),
          timestamp = Timestamp(first.timestamp).minus(1.minute),
          fields = List(
            StringField("platform", first.platform),
            NumberField("columns", first.columns)
          )
        )
    }

    val cts = queries.flatMap(q => {
      val ranking = RankingEvent(
        id = EventId.randomUUID,
        timestamp = Timestamp(q.timestamp),
        user = Some(UserId(q.user)),
        session = None,
        fields = List(StringField("query", q.query)),
        items = NonEmptyList.fromListUnsafe(q.movies.map(m => RankItem(ItemId(m.movie.toString), m.bm25)))
      )
      val ints = q.movies
        .filter(_.label)
        .map(m =>
          InteractionEvent(
            id = EventId.randomUUID,
            item = ItemId(m.movie.toString),
            timestamp = ranking.timestamp.plus(10.second),
            ranking = Some(ranking.id),
            user = Some(UserId(q.user)),
            session = None,
            `type` = "click"
          )
        )
      ints :+ ranking
    })
    (itemEvents.toList ++ userEvents.toList ++ cts.toList).sortBy(_.timestamp.ts)
  }

  def readcsv(file: File, skip: Int): Array[Array[String]] = {
    val parser = new CSVParserBuilder().withSeparator('\t').build()
    val reader = new CSVReaderBuilder(new InputStreamReader(file.newGzipInputStream()))
      .withSkipLines(skip)
      .withCSVParser(parser)
      .build()
    val result = reader.readAll().asScala.toArray
    reader.close()
    result
  }
}
