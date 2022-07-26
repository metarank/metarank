package ai.metarank.util.persistence.field

import ai.metarank.model.{Field, FieldId}
import ai.metarank.util.Logging
import ai.metarank.util.persistence.field.FieldStore.FieldStoreFactory
import io.circe.Codec
import io.findify.featury.connector.redis.persistence.RedisClient
import io.findify.featury.connector.redis.persistence.RedisClient.{PipelinedRedisClient, SimpleRedisClient}
import io.findify.featury.values.StoreCodec.JsonCodec
import redis.clients.jedis.Jedis

case class RedisFieldStore(client: RedisClient) extends FieldStore with Logging {
  val idCodec = new JsonCodec[FieldId] {
    override implicit val codec: Codec[FieldId] = FieldId.fieldIdCodec
  }
  val fieldCodec = new JsonCodec[Field] {
    override implicit val codec: Codec[Field] = Codec.from(Field.fieldDecoder, Field.fieldEncoder)
  }

  override def get(id: FieldId): Option[Field] = {
    val keyBytes = idCodec.encode(id)
    client.get(keyBytes).map(fieldCodec.decode) match {
      case Some(Right(value)) => Some(value)
      case Some(Left(error)) =>
        logger.error(s"cannot decode field with key=$id: ${error.getMessage}", error)
        None
      case None => None
    }
  }

  override def put(id: FieldId, value: Field): Unit = {
    val keyBytes   = idCodec.encode(id)
    val fieldBytes = fieldCodec.encode(value)
    client.set(keyBytes, fieldBytes)
  }
}

object RedisFieldStore {
  case class RedisFieldStoreFactory(host: String, port: Int, db: Int, pipelined: Boolean) extends FieldStoreFactory {
    def create(): FieldStore = {
      val jedis = new Jedis(host, port)
      jedis.select(db)
      val client = if (pipelined) PipelinedRedisClient(jedis) else SimpleRedisClient(jedis)
      RedisFieldStore(client)
    }
  }
}
