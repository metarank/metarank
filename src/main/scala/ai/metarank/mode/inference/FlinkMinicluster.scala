package ai.metarank.mode.inference

import ai.metarank.mode.inference.FlinkMinicluster.FlinkError
import ai.metarank.mode.upload.Upload.logger
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource

import scala.concurrent.duration._

case class FlinkMinicluster(cluster: MiniClusterWithClientResource, client: ClusterClient[_]) extends Logging {
  def waitForStatus(
      job: JobID,
      expectedStatus: JobStatus,
      timeout: Duration,
      took: Duration = 0.seconds
  ): IO[Unit] = for {
    _ <- IO.sleep(1.second)
    _ <- IO.fromCompletableFuture(IO { client.getJobStatus(job) }).flatMap {
      case s if s == expectedStatus => IO { logger.info(s"job $job is reached expected status $expectedStatus") }
      case other if took >= timeout =>
        IO.raiseError(FlinkError(s"job $job status=$other didn't reached expected $expectedStatus in $timeout"))
      case other =>
        IO { logger.warn(s"upload job not yet finished (status=$other), waiting for $took") } *> waitForStatus(
          job,
          expectedStatus,
          timeout,
          took + 1.second
        )
    }
  } yield {}

}

object FlinkMinicluster {
  case class FlinkError(msg: String) extends Exception(msg)
  def resource(conf: Configuration) = Resource.make(createCluster(conf))(shutdown)

  def createCluster(conf: Configuration) = IO {
    val cluster = new MiniClusterWithClientResource(
      new MiniClusterResourceConfiguration.Builder()
        .setNumberTaskManagers(1)
        .setNumberSlotsPerTaskManager(1)
        .setConfiguration(conf)
        .build()
    )
    cluster.before()
    val client = cluster.getClusterClient
    new FlinkMinicluster(cluster, client)
  }

  def shutdown(cluster: FlinkMinicluster) = IO {
    cluster.client.close()
    cluster.cluster.after()
  }
}
