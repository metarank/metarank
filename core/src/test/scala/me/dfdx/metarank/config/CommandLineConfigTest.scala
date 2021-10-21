package me.dfdx.metarank.config

import better.files.File
import me.dfdx.metarank.config.CommandLineConfig.CommandLineParsingError
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CommandLineConfigTest extends AnyFlatSpec with Matchers {
  it should "load default empty opts" in {
    CommandLineConfig.parse(List()) shouldBe Right(CommandLineConfig())
  }

  it should "load config" in {
    val conf = File.newTemporaryFile()
    CommandLineConfig.parse(List("--config", conf.toString())) shouldBe Right(CommandLineConfig(config = conf))
    conf.delete()
  }

  it should "override host and port" in {
    CommandLineConfig.parse(List("-h", "127.0.0.1", "-p", "1234")) shouldBe Right(
      CommandLineConfig(hostname = Some("127.0.0.1"), port = Some(1234))
    )
  }

  it should "fail on wrong port" in {
    CommandLineConfig.parse(List("-p", "123456")) should matchPattern { case Left(CommandLineParsingError(_)) =>
    }
  }

  it should "fail on missing config" in {
    val conf = File("/tmp/" + Random.nextInt().toString)
    CommandLineConfig.parse(List("-c", conf.toString())) should matchPattern { case Left(CommandLineParsingError(_)) =>
    }
  }
}
