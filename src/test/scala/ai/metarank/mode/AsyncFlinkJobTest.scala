package ai.metarank.mode

import ai.metarank.mode.inference.FlinkMinicluster
import ai.metarank.mode.upload.Upload
import cats.effect.unsafe.implicits.global
import org.apache.flink.api.common.JobStatus
import org.apache.flink.configuration.Configuration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.flink.api.scala._
import scala.concurrent.duration._

class AsyncFlinkJobTest extends AnyFlatSpec with Matchers {
  it should "not fail on finite jobs" in {
    val attempt = for {
      cluster <- FlinkMinicluster.resource(new Configuration())
      job     <- AsyncFlinkJob.execute(cluster) { env => env.fromCollection(List(1, 2, 3)).map(x => x + 1) }
    } yield {
      cluster -> job
    }

    val result = attempt.use(x => x._1.waitForStatus(x._2, JobStatus.FINISHED, 30.seconds))
    result.unsafeRunSync() shouldBe {}
  }
}
