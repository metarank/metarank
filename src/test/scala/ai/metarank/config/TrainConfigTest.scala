package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.config.TrainConfig.RedisTrainConfig
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.FileClickthroughStore
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

class TrainConfigTest extends AnyFlatSpec with Matchers {
  it should "load train config for redis state" in {
    val state = RedisStateConfig(Hostname("localhost"), Port(123))
    val conf = TrainConfig.fromState(state)
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

  it should "load file config when file is a file" in {
    val yaml =
      """type: file
        |path: /tmp/whatever.bin
        |format: binary""".stripMargin
    val confOpt = parse(yaml).flatMap(_.as[TrainConfig])
    val (result, _) = ClickthroughStore.fromConfig(confOpt.toOption.get).allocated.unsafeRunSync()
    result should matchPattern {
      case FileClickthroughStore(file, _, _, _) if file.toString == "/tmp/whatever.bin" => // yep
    }
  }

  it should "fail file config when file is a dir" in {
    val yaml =
      """type: file
        |path: /
        |format: binary""".stripMargin
    val confOpt = parse(yaml).flatMap(_.as[TrainConfig])
    confOpt shouldBe a[Left[_, _]]
  }
}