package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.{KVCodec, StreamStore}
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.LPUSH
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.std.Queue
import fs2.Chunk
import cats.implicits._

case class RedisStreamStore[V](name: String, queue: Queue[IO, RedisOp], client: RedisReader)(implicit vc: KVCodec[V])
    extends StreamStore[V]
    with Logging {
  override def push(values: List[V]): IO[Unit] =
    queue.offer(LPUSH(name, values.map(vc.encode)))

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
