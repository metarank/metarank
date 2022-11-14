package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.config.TrainConfig.RedisTrainConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TrainConfigTest extends AnyFlatSpec with Matchers {
  it should "load train config for redis state" in {
    val state = RedisStateConfig(Hostname("localhost"), Port(123))
    val conf  = TrainConfig.fromState(state)
    conf shouldBe RedisTrainConfig(
      host = state.host,
      port = state.port,
      db = state.db.rankings,
      cache = state.cache,
      pipeline = state.pipeline,
      format = state.format,
      auth = state.auth,
      tls = state.tls,
      timeout = state.timeout
    )
  }
}
