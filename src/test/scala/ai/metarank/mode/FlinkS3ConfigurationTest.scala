package ai.metarank.mode

import org.apache.flink.configuration.{ConfigOption, ConfigOptions}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FlinkS3ConfigurationTest extends AnyFlatSpec with Matchers {
  it should "strip secrets from logging" in {
    FlinkS3Configuration.stripSecrets("QWERTYUIOP1234") shouldBe "xxxxxxxxxx1234"
    FlinkS3Configuration.stripSecrets("OP1234") shouldBe "xx1234"
  }

  it should "load secrets from env" in {
    val conf = FlinkS3Configuration(
      Map(
        "AWS_ACCESS_KEY_ID"     -> "QWE1234",
        "AWS_SECRET_ACCESS_KEY" -> "QWE1234",
        "AWS_S3_ENDPOINT_URL"   -> "localhost"
      )
    )
    conf.getString(ConfigOptions.key("s3.access-key").stringType().noDefaultValue()) shouldBe "QWE1234"
    conf.getString(ConfigOptions.key("s3.endpoint").stringType().noDefaultValue()) shouldBe "localhost"
  }
}
