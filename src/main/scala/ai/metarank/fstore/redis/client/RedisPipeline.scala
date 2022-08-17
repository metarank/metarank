package ai.metarank.fstore.redis.client

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import cats.effect.IO
import cats.effect.kernel.Resource
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{RedisCodec, StringCodec}
import scala.jdk.CollectionConverters._

case class RedisPipeline(
    client: RedisClient,
    commands: RedisAsyncCommands[String, String],
    conn: StatefulRedisConnection[String, String]
) {
  def batch(ops: List[RedisOp]): IO[Unit] = {
    ops.foreach {
      case RedisOp.LPUSH(key, value)         => commands.lpush(key, value: _*)
      case RedisOp.SET(key, value)           => commands.set(key, value)
      case RedisOp.INCRBY(key, by)           => commands.incrby(key, by)
      case RedisOp.HDEL(key, hashKey)        => commands.hdel(key, hashKey)
      case RedisOp.HSET(key, values)         => commands.hset(key, values.asJava)
      case RedisOp.HINCRBY(key, hashKey, by) => commands.hincrby(key, hashKey, by)
      case RedisOp.LTRIM(key, start, end)    => commands.ltrim(key, start, end)
      case RedisOp.MSET(kvs)                 => commands.mset(kvs.asJava)
    }
    IO(conn.flushCommands())
  }
}

object RedisPipeline {
  def create(host: String, port: Int, db: Int): Resource[IO, RedisPipeline] = {
    Resource.make(IO {
      val client = io.lettuce.core.RedisClient.create(s"redis://$host:$port")
      val conn   = client.connect[String, String](RedisCodec.of(new StringCodec(), new StringCodec()))
      conn.sync().select(db)
      conn.setAutoFlushCommands(false)
      new RedisPipeline(client, conn.async(), conn)
    })(client => IO(client.client.close()))
  }

  sealed trait RedisOp

  object RedisOp {
    case class LPUSH(key: String, values: List[String]) extends RedisOp
    object LPUSH {
      def apply(key: String, value: String) = new LPUSH(key, List(value))
    }
    case class SET(key: String, value: String)                extends RedisOp
    case class INCRBY(key: String, by: Int)                   extends RedisOp
    case class HDEL(key: String, hashKey: String)             extends RedisOp
    case class HSET(key: String, values: Map[String, String]) extends RedisOp
    object HSET {
      def apply(key: String, hashKey: String, hashValue: String) = new HSET(key, Map(hashKey -> hashValue))
    }
    case class HINCRBY(key: String, hashKey: String, by: Int) extends RedisOp
    case class LTRIM(key: String, start: Int, end: Int)       extends RedisOp
    case class MSET(kv: Map[String, String])                  extends RedisOp
  }

}
