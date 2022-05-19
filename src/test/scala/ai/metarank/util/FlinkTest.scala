package ai.metarank.util

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import io.findify.flink.api._

trait FlinkTest {
  lazy val env = FlinkTest.env
}

object FlinkTest {
  lazy val env = {
    val e = StreamExecutionEnvironment.createLocalEnvironment(1)
    e.setParallelism(1)
    e.setRestartStrategy(RestartStrategies.noRestart())
    e.setStateBackend(new EmbeddedRocksDBStateBackend())
    e.getConfig.enableObjectReuse()
    e
  }
}
