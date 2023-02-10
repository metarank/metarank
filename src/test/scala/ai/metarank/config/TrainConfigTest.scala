package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.config.TrainConfig.{FileTrainConfig, RedisTrainConfig}
import ai.metarank.fstore.ClickthroughStore
import ai.metarank.fstore.clickthrough.FileClickthroughStore
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

import java.nio.file.Files
import scala.util.Try

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

  it should "load file config when file is a file" in {
    val path = Files.createTempFile("metarank-cts", "tmp")
    path.toFile.delete()
    val yaml =
      s"""type: file
         |path: '$path'
         |format: binary""".stripMargin
    val confOpt = parse(yaml).flatMap(_.as[TrainConfig])
    val result  = Try(ClickthroughStore.fromConfig(confOpt.toOption.get).allocated.unsafeRunSync()._1)
    result.isSuccess shouldBe true
  }

  it should "load file config with conflict policy" in {
    val yaml =
      s"""type: file
         |path: /foo/a.bin
         |whenExists: append
         |format: binary""".stripMargin
    val conf = parse(yaml).flatMap(_.as[TrainConfig])
    conf shouldBe Right(FileTrainConfig("/foo/a.bin"))
  }

  it should "fail file config when file is a file" in {
    val path = Files.createTempFile("metarank", ".bin")
    val yaml =
      s"""type: file
         |path: '$path'
         |format: binary""".stripMargin

    val confOpt = parse(yaml).flatMap(_.as[TrainConfig])
    val result  = Try(ClickthroughStore.fromConfig(confOpt.toOption.get).allocated.unsafeRunSync()._1)
    result.isFailure shouldBe true
  }
}
