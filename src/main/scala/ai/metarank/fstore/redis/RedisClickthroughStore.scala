package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{ClickthroughStore, KVCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, EventId, Identifier, ItemValue, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Stream}
import cats.implicits._
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

case class RedisClickthroughStore(rankings: RedisClient, hist: RedisClient, ctPrefix: String, histPrefix: String)
    extends ClickthroughStore
    with Logging {
  import RedisClickthroughStore._
  val BATCH_SIZE = 512

  override def putRanking(ranking: Event.RankingEvent): IO[Unit] =
    rankings
      .set(
        ctPrefix + "/" + ranking.id.value,
        cts.encode(Clickthrough(ranking.timestamp, ranking.items.toList.map(_.id)))
      )
      .void

  override def putValues(id: EventId, values: List[ItemValue]): IO[Unit] =
    hist.set(histPrefix + "/" + id.value, ivc.encode(values)).void

  override def putInteraction(id: EventId, item: Identifier.ItemId, tpe: String): IO[Unit] =
    rankings.append(ctPrefix + "/" + id.value, s"${item.value}@$tpe,").void

  override def getClickthrough(id: EventId): IO[Option[Clickthrough]] =
    rankings.get(ctPrefix + "/" + id.value).flatMap {
      case None      => IO.pure(None)
      case Some(str) => IO.fromEither(cts.decode(str)).map(Option.apply)
    }

  override def getall(): fs2.Stream[IO, ClickthroughValues] = Stream
    .unfoldLoopEval[IO, String, List[ClickthroughValues]]("0")(cursor =>
      for {
        scanned <- rankings.scan(cursor, BATCH_SIZE)
        cts     <- rankings.mget(scanned.keys).flatMap(decodeMap[Clickthrough](ctPrefix, _))
        values  <- hist.mget(scanned.keys).flatMap(decodeMap[List[ItemValue]](histPrefix, _))
        _       <- info(s"fetched next page of ${cts.size} clickthroughs, ${values.size} feature values")
      } yield {
        val decoded = for {
          (id, ct) <- cts if ct.interactions.nonEmpty
          vals     <- values.get(id)
        } yield {
          ClickthroughValues(ct, vals)
        }
        scanned.cursor match {
          case "0"  => decoded.toList -> None
          case next => decoded.toList -> Some(next)
        }
      }
    )
    .flatMap(batch => Stream.emits(batch))

  private def decodeMap[T](prefix: String, map: Map[String, String])(implicit dec: KVCodec[T]): IO[Map[String, T]] = {
    map.toList
      .map { case (key, value) =>
        IO.fromEither(dec.decode(value)).map(value => key.substring(prefix.length + 1) -> value)
      }
      .sequence
      .map(_.toMap)
  }
}

object RedisClickthroughStore {
  import io.circe.generic.semiauto._
  val ivj                                    = Codec.from(Decoder.decodeList[ItemValue], Encoder.encodeList[ItemValue])
  implicit val ivc: KVCodec[List[ItemValue]] = KVCodec.jsonCodec[List[ItemValue]](ivj)
  implicit val cts: KVCodec[Clickthrough] = new KVCodec[Clickthrough] {
    def decodeItem(str: String): Either[Throwable, ItemId] = Right(ItemId(str))
    def decodeInt(str: String): Either[Throwable, TypedInteraction] = {
      val sep = str.indexOf('@'.toInt)
      if (sep > 0) {
        Right(TypedInteraction(ItemId(str.substring(0, sep)), str.substring(sep + 1)))
      } else {
        Left(new Exception(s"cannot parse interaction $str"))
      }
    }
    override def decode(str: String): Either[Throwable, Clickthrough] = str.split('|').toList match {
      case tsStr :: itemsStr :: intsStr :: Nil =>
        for {
          ts    <- tsStr.toLongOption.map(Timestamp.apply).toRight(new Exception(s"cannot parse ct $str"))
          items <- decodeRec[ItemId](itemsStr.split(',').toList, decodeItem)
          ints  <- decodeRec[TypedInteraction](intsStr.split(',').toList, decodeInt)
        } yield {
          Clickthrough(ts, items, ints)
        }
      case other => Left(new Exception(s"cannot decode ct $other"))
    }

    override def encode(value: Clickthrough): String = {
      val ts    = value.ts.ts.toString
      val items = value.items.map(_.value).mkString(",")
      val ints  = value.interactions.map(int => s"${int.item.value}@${int.tpe}").mkString("", ",", ",")
      s"$ts|$items|$ints"
    }
  }

  private def decodeRec[T](
      values: List[String],
      decodeOne: String => Either[Throwable, T],
      acc: List[T] = Nil
  ): Either[Throwable, List[T]] = values match {
    case Nil        => Right(acc)
    case "" :: tail => decodeRec(tail, decodeOne, acc)
    case head :: tail =>
      decodeOne(head) match {
        case Left(err)    => Left(err)
        case Right(value) => decodeRec(tail, decodeOne, value +: acc)
      }
  }

}
