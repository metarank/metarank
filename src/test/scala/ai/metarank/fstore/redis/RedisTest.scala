package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisReader}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}

trait RedisTest extends BeforeAndAfterAll { this: Suite =>
  lazy val client                  = RedisReader.create("localhost", 6379, 0).allocated.unsafeRunSync()._1
  lazy val writer                  = RedisPipeline.create("localhost", 6379, 0).allocated.unsafeRunSync()._1
  val pipeline: Queue[IO, RedisOp] = Queue.unbounded[IO, RedisOp].unsafeRunSync()

  override def beforeAll() = {
    super.beforeAll()
    client.commands.flushall().get()
  }

  def flushPipeline(): IO[Unit] = fs2.Stream
    .fromQueueUnterminated(pipeline)
    .take(1)
    .foreach(op => writer.batch(List(op)))
    .compile
    .drain

}
