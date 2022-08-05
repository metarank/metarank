package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, StreamStore}
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.LPUSH
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import fs2.Chunk
import cats.implicits._

case class RedisStreamStore[V](name: String, client: RedisClient)(implicit vc: KVCodec[V])
    extends StreamStore[V]
    with Logging {
  override def push(values: List[V]): IO[Unit] = {
    client.lpush(name, values.map(vc.encode)).void
  }

  override def getall(): fs2.Stream[IO, V] =
    fs2.Stream.unfoldChunkEval(0)(start =>
      client.lrange(name, start, 100).flatMap {
        case Nil => debug(s"scan: no more items for list $name") *> IO.pure(None)
        case list =>
          list
            .map(str => IO.fromEither(vc.decode(str)))
            .sequence
            .map(decoded => Some(Chunk.seq(decoded) -> decoded.size))
      }
    )
}
