package ai.metarank.util

import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, RankingEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{Event, EventId, Timestamp}
import cats.data.NonEmptyList
import io.circe.syntax._

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.util.UUID
import scala.concurrent.duration._
import scala.util.Random

object SyntheticRanklensDataset {
  def main(args: Array[String]): Unit = {
    for {
      users <- List(128, 256, 512, 1024, 2048, 4096)
    } {
      println(users)
      val events = apply(users = users * 1000)
      val file   = new File(s"/home/shutty/work/metarank/ranklens-syn/events${users}.jsonl")
      val stream = new BufferedOutputStream(new FileOutputStream(file), 128 * 1024)
      events.foreach(e => {
        stream.write(e.asJson.noSpaces.getBytes())
        stream.write('\n')
      })
      stream.close()
    }
  }
  def apply(
      start: Timestamp = Timestamp.date(2022, 9, 1, 0, 0, 0),
      period: FiniteDuration = 30.days,
      items: Int = 100000,
      users: Int = 1000,
      rankingsPerUser: Int = 2,
      clicksPerRanking: Int = 2
  ): Iterator[Event] = {
    val events   = RanklensEvents().collect { case i: ItemEvent => i }
    val genres   = events.flatMap(_.fields.collect { case g @ StringListField("genres", _) => g }).toArray
    val director = events.flatMap(_.fields.collect { case g @ StringField("director", _) => g }).toArray
    val tags     = events.flatMap(_.fields.collect { case g @ StringListField("tags", _) => g }).toArray
    val actors   = events.flatMap(_.fields.collect { case g @ StringListField("actors", _) => g }).toArray

    val step = (period.toMillis / (users * rankingsPerUser * clicksPerRanking)).millis
    val itemEvents = for {
      i <- (0 until items).iterator
    } yield {
      ItemEvent(
        id = EventId(UUID.randomUUID().toString),
        item = ItemId(i.toString),
        timestamp = start,
        fields = List(
          genres(Random.nextInt(genres.length)),
          tags(Random.nextInt(tags.length)),
          actors(Random.nextInt(actors.length)),
          director(Random.nextInt(director.length)),
          StringField("title", s"Film ${i}"),
          NumberField("popularity", Random.nextInt(100000)),
          NumberField("vote_avg", Random.nextInt(10)),
          NumberField("vote_cnt", Random.nextInt(1000)),
          NumberField("budget", Random.nextInt(100000000)),
          NumberField("runtime", 50 + Random.nextInt(120))
        )
      )
    }

    val rankings = for {
      user <- (0 until users).iterator
      i    <- 0 until rankingsPerUser
    } yield {
      val itemList = (0 until 10).map(_ => RankItem(ItemId(Random.nextInt(items).toString), 1.0)).toArray
      val rank = RankingEvent(
        id = EventId(UUID.randomUUID().toString),
        timestamp = start.plus((user * rankingsPerUser + i) * step),
        user = Some(UserId(user.toString)),
        session = Some(SessionId(user.toString)),
        items = NonEmptyList.fromListUnsafe(itemList.toList)
      )
      val clickPause = step.toMillis / (clicksPerRanking + 1)
      val clicks = for {
        i <- (0 until clicksPerRanking).toList
      } yield {
        InteractionEvent(
          id = EventId(UUID.randomUUID().toString),
          item = itemList(Random.nextInt(itemList.length)).id,
          timestamp = rank.timestamp.plus(clickPause.millis),
          user = rank.user,
          session = rank.session,
          ranking = Some(rank.id),
          `type` = "click"
        )
      }
      val b = 1
      Iterator.single(rank) ++ clicks.iterator
    }

    itemEvents ++ rankings.flatten
  }
}
