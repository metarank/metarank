package ai.metarank.util

import ai.metarank.feature.FeatureMapping
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, MetadataEvent, RankingEvent}
import ai.metarank.model.FieldName.{Impression, Interaction}
import ai.metarank.model.Field.{BooleanField, NumberField, StringField}
import ai.metarank.model.{Event, EventId, FieldName, Field, FieldSchema, ItemId, SessionId, UserId}
import io.findify.featury.model.Timestamp
import org.scalacheck.Gen

object EventGen {

  val genUser      = Gen.listOfN(8, Gen.alphaChar).map(c => UserId(c.mkString))
  val genSession   = Gen.listOfN(8, Gen.alphaChar).map(c => SessionId(c.mkString))
  val genId        = Gen.uuid.map(uuid => EventId(uuid.toString))
  val genItem      = Gen.posNum[Int].map(i => ItemId("p" + i))
  val genTimestamp = Gen.chooseNum[Int](0, 60 * 60 * 1000).map(offset => Timestamp(System.currentTimeMillis() + offset))
  val itemRelGen = for {
    id    <- genItem
    score <- Gen.chooseNum[Double](0.0, 1.0)
  } yield {
    ItemRelevancy(id, score)
  }

  def fieldGen(fields: Map[String, FieldSchema]): Gen[Option[Field]] = {
    for {
      fieldName <- Gen.oneOf(fields.keys)
      field <- fields.get(fieldName) match {
        case Some(FieldSchema.BooleanFieldSchema(name, source, required)) =>
          reqOption(Gen.oneOf(true, false).map(b => BooleanField(name, b)), required)
        case Some(FieldSchema.NumberFieldSchema(name, source, required)) =>
          reqOption(Gen.chooseNum(0, 100).map(i => NumberField(name, i)), required)
        case Some(FieldSchema.StringFieldSchema(name, source, required)) =>
          reqOption(Gen.alphaStr.map(s => StringField(name, s)), required)
        case _ => Gen.const(None)
      }
    } yield {
      field
    }
  }

  def reqOption[T](gen: Gen[T], required: Boolean): Gen[Option[T]] =
    if (required) gen.map(Some.apply) else Gen.option(gen)

  def metadataGen(fields: Map[String, FieldSchema]) = for {
    id     <- genId
    item   <- genItem
    ts     <- genTimestamp
    fields <- Gen.listOfN(10, fieldGen(fields)).map(_.flatten.distinct)
  } yield {
    MetadataEvent(id, item, ts, fields)
  }

  def rankingGen(fields: Map[String, FieldSchema]) = for {
    id      <- genId
    ts      <- genTimestamp
    fields  <- Gen.listOfN(3, fieldGen(fields)).map(_.flatten.distinct)
    user    <- genUser
    session <- genSession
    items   <- Gen.listOfN(10, itemRelGen)
  } yield {
    RankingEvent(id, ts, user, session, fields, items)
  }

  def interactionGen(fields: Map[String, FieldSchema]) = for {
    id      <- genId
    item    <- genItem
    ts      <- genTimestamp
    impr    <- genId
    fields  <- Gen.listOfN(10, fieldGen(fields)).map(_.flatten.distinct)
    user    <- genUser
    session <- genSession
    tpe     <- Gen.oneOf("click", "purchase")
  } yield {
    InteractionEvent(id, item, ts, impr, user, session, tpe, fields)
  }

  def eventGen(mapping: FeatureMapping) = Gen.oneOf[Event](
    metadataGen(mapping.fields.filter(_.source.event == FieldName.Metadata).map(x => x.name -> x).toMap),
    rankingGen(mapping.fields.filter(_.source.event == FieldName.Ranking).map(x => x.name -> x).toMap),
    interactionGen(
      mapping.fields
        .collect { case f @ FieldSchema(_, _, FieldName(Interaction(_), _)) => f }
        .map(x => x.name -> x)
        .toMap
    )
  )

  implicit class SchemaMapOps(self: List[FieldSchema]) {
    def asMap: Map[String, FieldSchema] = self.map(s => s.name -> s).toMap
  }
}
