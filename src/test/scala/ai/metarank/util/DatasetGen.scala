package ai.metarank.util

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.model.{Event, EventId}
import ai.metarank.model.Event.{ItemEvent, ItemRelevancy, RankingEvent, UserEvent}
import ai.metarank.model.Field.BooleanField
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import cats.data.NonEmptyList
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.Timestamp
import org.scalacheck.Gen

//object DatasetGen {
//
//  val now                        = Timestamp.date(2022, 4, 1, 0, 0, 1)
//  val tenant                     = Tenant("default")
//  val userGen: Gen[UserId]       = Gen.chooseNum(1, 10000).map(i => UserId("user" + i.toString))
//  val sessionGen: Gen[SessionId] = Gen.chooseNum(1, 10000).map(i => SessionId("session" + i.toString))
//  val itemGen: Gen[ItemId]       = Gen.chooseNum(1, 1000).map(i => ItemId("item" + i.toString))
//  val idGen: Gen[EventId]        = Gen.uuid.map(uuid => EventId(uuid.toString))
//
//  def booleanFeature(schema: BooleanFeatureSchema): Gen[Event] = for {
//    id      <- idGen
//    item    <- itemGen
//    user    <- userGen
//    session <- sessionGen
//    field   <- Gen.oneOf(true, false).map(BooleanField(schema.source.field, _))
//  } yield {
//    schema.source.event match {
//      case EventType.Item => ItemEvent(id, item, now, List(field))
//      case EventType.User => UserEvent(id, user, now, List(field))
//      case EventType.Ranking =>
//        RankingEvent(id, now, user, session, List(field), NonEmptyList.of(ItemRelevancy(item, 1.0)))
//    }
//  }
//
//}
