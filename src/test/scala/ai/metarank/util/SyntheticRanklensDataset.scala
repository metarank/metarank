package ai.metarank.util

import ai.metarank.model.Event.{InteractionEvent, ItemEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{Event, EventId, Timestamp}
import cats.data.NonEmptyList

import java.util.UUID
import scala.concurrent.duration._
import scala.util.Random

object SyntheticRanklensDataset {
  def main(args: Array[String]): Unit = {
    val events = apply(users = 10000)
    val br     = 1
  }
  def apply(
      start: Timestamp = Timestamp.date(2022, 9, 1, 0, 0, 0),
      period: FiniteDuration = 30.days,
      items: Int = 1000,
      users: Int = 1000,
      rankingsPerUser: Int = 10,
      clicksPerRanking: Int = 2
  ) = {
    val events   = RanklensEvents().collect { case i: ItemEvent => i }
    val genres   = events.flatMap(_.fields.collect { case g @ StringListField("genres", _) => g }).toArray
    val director = events.flatMap(_.fields.collect { case g @ StringField("director", _) => g }).toArray
    val tags     = events.flatMap(_.fields.collect { case g @ StringListField("tags", _) => g }).toArray
    val actors   = events.flatMap(_.fields.collect { case g @ StringListField("actors", _) => g }).toArray

    val itemEvents = for {
      i <- (0 until items).toArray
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

    val userIds = (0 until users).map(i => UserId(i.toString)).toArray

    val rankings = for {
      user <- userIds
      i    <- 0 until rankingsPerUser
    } yield {
      RankingEvent(
        id = EventId(UUID.randomUUID().toString),
        timestamp = start.plus(Random.nextLong(period.toMillis).millis),
        user = user,
        session = Some(SessionId(user.value)),
        items = NonEmptyList.fromListUnsafe(
          (0 until 10).map(_ => ItemRelevancy(itemEvents(Random.nextInt(itemEvents.length)).item, 1.0)).toList
        )
      )
    }

    val clicks = for {
      ranking       <- rankings
      (item, index) <- Random.shuffle(ranking.items.map(_.id).toList).take(clicksPerRanking).zipWithIndex
    } yield {
      InteractionEvent(
        id = EventId(UUID.randomUUID().toString),
        item = item,
        timestamp = ranking.timestamp.plus((10 + index * Random.nextInt(10)).seconds),
        user = ranking.user,
        session = ranking.session,
        ranking = Some(ranking.id),
        `type` = "click"
      )
    }

    (itemEvents.toList ++ rankings.toList ++ clicks.toList).sortBy(_.timestamp.ts)
  }
}
