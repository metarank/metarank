package ai.metarank.mode.inference

import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource

case class FlinkMinicluster(cluster: MiniClusterWithClientResource, client: ClusterClient[_])

object FlinkMinicluster {
  def resource(conf: Configuration) = Resource.make(createCluster(conf))(shutdown)

  private def createCluster(conf: Configuration) = IO {
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

  private def shutdown(cluster: FlinkMinicluster) = IO {
    cluster.client.close()
    cluster.cluster.after()
  }
}
