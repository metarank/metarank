package ai.metarank.main

import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.main.CliArgs.{ImportArgs, ServeArgs, TrainArgs}
import ai.metarank.source.format.JsonFormat
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class CliArgsTest extends AnyFlatSpec with Matchers {
  lazy val conf = Files.createTempFile("metarank-test-conf", ".yaml")
  lazy val data = Files.createTempFile("metarank-test-data", ".json")
  it should "parse serve" in {
    CliArgs.parse(List("serve", "-c", conf.toString)) shouldBe Right(ServeArgs(conf))
    CliArgs.parse(List("serve", "--config", conf.toString)) shouldBe Right(ServeArgs(conf))

  }

  it should "die on wrong options" in {
    val result = CliArgs.parse(List("serve", "-yolo"))
    result.isLeft shouldBe true
  }

  it should "parse import, short" in {
    CliArgs.parse(List("import", "-c", conf.toString, "-d", data.toString)) shouldBe Right(
      ImportArgs(conf, data, SourceOffset.Earliest, JsonFormat)
    )
  }

  it should "parse import, long" in {
    CliArgs.parse(List("import", "-c", conf.toString, "-d", data.toString)) shouldBe Right(
      ImportArgs(conf, data, SourceOffset.Earliest, JsonFormat)
    )
  }

  it should "parse train args, short" in {
    CliArgs.parse(List("train", "-c", conf.toString, "-m", "xgboost")) shouldBe Right(
      TrainArgs(conf, "xgboost")
    )
  }

}
