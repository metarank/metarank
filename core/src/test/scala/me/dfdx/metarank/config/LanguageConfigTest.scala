package me.dfdx.metarank.config

import io.circe.DecodingFailure
import io.circe.yaml.parser.parse
import me.dfdx.metarank.config.Config.WindowConfig
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.model.Language.LanguageLoadingError
import me.dfdx.metarank.model.{Language, Nel}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LanguageConfigTest extends AnyFlatSpec with Matchers {
  import Config._
  it should "decode supported language" in {
    parse("en").flatMap(_.as[Language]).map(_.code) shouldBe Right("en")
  }

  it should "fail on unsupported lang" in {
    val yaml = "wtf"
    parse("wtf").flatMap(_.as[Language]).map(_.code) should matchPattern { case Left(DecodingFailure(_, _)) =>
    }
  }
}
