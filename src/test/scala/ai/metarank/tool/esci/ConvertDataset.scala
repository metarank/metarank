package ai.metarank.tool.esci

import ai.metarank.flow.PrintProgress
import ai.metarank.model.Event.{ItemEvent, RankItem, RankingEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.{Event, EventId, Field, Timestamp}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.tool.esci.GenerateLLMTraining.Query
import ai.metarank.tool.esci.Page.{BookPage, ContentPage}
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import fs2.io.file.{Files, Path}

import scala.concurrent.duration._
import io.circe.syntax._

object ConvertDataset extends IOApp with Logging {
  val now = Timestamp.now
  override def run(args: List[String]): IO[ExitCode] = args match {
    case parsedPath :: rankingPath :: Nil =>
      for {
        seen <- GenerateLLMTraining
          .loadQueries(rankingPath)
          .map(_.filter(q => q.small && (q.locale == "us")).flatMap(_.products.map(_.asin)).toSet)
        queries <- GenerateLLMTraining
          .loadQueriesStream(rankingPath)
          .map(
            _.filter(q => q.small && (q.locale == "us")).through(PrintProgress.tap(None, "queries")).zipWithIndex.map {
              case (q, i) => makeRankingEvent(q, i)
            }
          )
        items = GenerateLLMTraining
          .loadPages(parsedPath)
          .filter(_.locale == "us")
          .filter(p => seen.contains(p.asin))
          .collect { case p: ContentPage =>
            makeItemEvent(p)
          }
          .through(PrintProgress.tap(None, "items"))
        combined: Stream[IO, Event] = items ++ queries
        _ <- combined
          .map(_.asJson.noSpaces + "\n")
          .through(fs2.text.utf8.encode)
          .through(Files[IO].writeAll(Path("/tmp/events-small.jsonl")))
          .compile
          .drain
      } yield {
        ExitCode.Success
      }
    case _ => IO.raiseError(new Exception("need path to esci.json.zst"))
  }

  def makeItemEvent(page: ContentPage): ItemEvent = {
    val extra = List(
      page.starsNumeric.map(s => NumberField("stars", s)),
      page.ratingsNumeric.map(s => NumberField("ratings", s)),
      page.category.headOption.map(c => StringField("category0", c)),
      page.category.lift(1).map(c => StringField("category1", c)),
      page.category.lift(2).map(c => StringField("category2", c)),
      page.color.map(c => StringListField("color", c)),
      page.weight.map(w => NumberField("weight", w)),
      page.material.map(m => StringListField("material", m)),
      page.priceNumeric.map(p => NumberField("price", p))
    ).flatten[Field]

    ItemEvent(
      id = EventId.randomUUID,
      item = ItemId(page.asin),
      timestamp = now,
      fields = List(
        StringField("title", page.title),
        StringField("desc", page.desc),
        StringField("template", page.template)
      ) ++ extra
    )
  }

  val rels = Map("E" -> 4, "S" -> 3, "C" -> 2, "I" -> 1)

  def makeRankingEvent(q: Query, index: Long): RankingEvent = RankingEvent(
    id = EventId(q.qid),
    timestamp = now.plus(index.second),
    user = None,
    session = None,
    fields = List(StringField("query", q.query), StringField("split", q.split)),
    items = NonEmptyList.fromListUnsafe(q.products.map(p => RankItem(ItemId(p.asin), label = rels.get(p.label))))
  )
}
