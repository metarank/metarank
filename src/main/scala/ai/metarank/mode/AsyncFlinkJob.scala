package ai.metarank.mode

import ai.metarank.mode.standalone.FlinkMinicluster
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.flink.api.common.JobStatus
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.util.TestStreamEnvironment
import io.findify.flink.api._

import java.util.concurrent.CompletableFuture
import scala.util.Random

object AsyncFlinkJob extends Logging {
  import ai.metarank.flow.DataStreamOps._
  def execute(cluster: FlinkMinicluster, savepoint: Option[String] = None, name: Option[String] = None)(
      job: (StreamExecutionEnvironment) => Unit
  ) =
    Resource.make(IO.fromCompletableFuture {
      IO {
        val env = new StreamExecutionEnvironment(new TestStreamEnvironment(cluster.cluster.getMiniCluster, 1))
        job(env)
        val streamGraph = env.getStreamGraph
        name match {
          case Some(realName) => streamGraph.setJobName(realName)
          case None           => streamGraph.setJobName(s"metarank-async-job-${math.abs(Random.nextInt())}")
        }

        savepoint.foreach(s => streamGraph.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(s, true)))
        logger.info(s"submitted job $name to local cluster")
        cluster.client.submitJob(streamGraph.getJobGraph)
      }
    })(job =>
      eval { cluster.client.getJobStatus(job) }.flatMap {
        case JobStatus.FINISHED => IO.unit
        case other =>
          eval(cluster.client.cancel(job)).map(_ => {}).handleErrorWith { case ex: Throwable =>
            IO(logger.error(s"cannot terminate job $job with status $other", ex)) *> IO.unit
          }
      }
    )

  def eval[T](future: => CompletableFuture[T]): IO[T] = IO.fromCompletableFuture(IO { future })
}
