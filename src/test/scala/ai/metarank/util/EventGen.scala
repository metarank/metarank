package ai.metarank.util

import ai.metarank.config.Config.SchemaConfig
import ai.metarank.model.Event.{ImpressionEvent, InteractionEvent, ItemRelevancy, MetadataEvent}
import ai.metarank.model.Field.{BooleanField, NumberField, StringField}
import ai.metarank.model.{Event, EventId, Field, FieldSchema, ItemId, SessionId, UserId}
import io.findify.featury.model.Timestamp
import org.scalacheck.Gen

object EventGen {

  val genUser      = Gen.alphaStr.map(UserId.apply)
  val genSession   = Gen.alphaStr.map(SessionId.apply)
  val genId        = Gen.uuid.map(uuid => EventId(uuid.toString))
  val genItem      = Gen.posNum[Int].map(i => ItemId("p" + i))
  val genTimestamp = Gen.chooseNum[Int](0, 60 * 60 * 1000).map(offset => Timestamp(System.currentTimeMillis() + offset))
  val itemRelGen = for {
    id    <- genItem
    score <- Gen.chooseNum[Double](0.0, 1.0)
  } yield {
    ItemRelevancy(id, score)
  }

  def fieldGen(fields: Map[String, FieldSchema]): Gen[Field] = {
    for {
      fieldName <- Gen.oneOf(fields.keys)
      field <- fields.get(fieldName) match {
        case Some(FieldSchema.BooleanFieldSchema(name, _)) => Gen.oneOf(true, false).map(b => BooleanField(name, b))
        case Some(FieldSchema.NumberFieldSchema(name, _))  => Gen.chooseNum(0, 100).map(i => NumberField(name, i))
        case Some(FieldSchema.StringFieldSchema(name, _))  => Gen.alphaStr.map(s => StringField(name, s))
        case _                                             => ???
      }
    } yield {
      field
    }
  }

  def metadataGen(fields: Map[String, FieldSchema]) = for {
    id     <- genId
    item   <- genItem
    ts     <- genTimestamp
    fields <- Gen.listOfN(3, fieldGen(fields))
  } yield {
    MetadataEvent(id, item, ts, fields)
  }

  def impressionGen(fields: Map[String, FieldSchema]) = for {
    id      <- genId
    ts      <- genTimestamp
    fields  <- Gen.listOfN(3, fieldGen(fields))
    user    <- genUser
    session <- genSession
    items   <- Gen.listOfN(3, itemRelGen)
  } yield {
    ImpressionEvent(id, ts, user, session, fields, items)
  }

  def interactionGen(fields: Map[String, FieldSchema]) = for {
    id      <- genId
    item    <- genItem
    ts      <- genTimestamp
    impr    <- genId
    fields  <- Gen.listOfN(3, fieldGen(fields))
    user    <- genUser
    session <- genSession
    tpe     <- Gen.oneOf("click", "purchase")
  } yield {
    InteractionEvent(id, item, ts, impr, user, session, tpe, fields)
  }

  def eventGen(schema: SchemaConfig) = Gen.oneOf[Event](
    metadataGen(schema.metadata.asMap),
    impressionGen(schema.impression.asMap),
    interactionGen(schema.interaction.asMap)
  )

  implicit class SchemaMapOps(self: List[FieldSchema]) {
    def asMap: Map[String, FieldSchema] = self.map(s => s.name -> s).toMap
  }
}
