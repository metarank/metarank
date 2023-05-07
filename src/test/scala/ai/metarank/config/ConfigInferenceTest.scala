package ai.metarank.config

import ai.metarank.ml.onnx.ModelHandle.HuggingFaceHandle
import ai.metarank.ml.onnx.encoder.EncoderConfig.BiEncoderConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

class ConfigInferenceTest extends AnyFlatSpec with Matchers {
  it should "parse inference only config" in {
    val yaml =
      """
        |inference:
        |  bi:
        |    type: bi-encoder
        |    model: metarank/all-MiniLM-L6-v2
        |    dim: 384
        |""".stripMargin
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed shouldBe Right(
      Config(inference =
        Map(
          "bi" -> BiEncoderConfig(
            model = Some(HuggingFaceHandle("metarank", "all-MiniLM-L6-v2")),
            dim = 384
          )
        )
      )
    )
  }

  it should "pick existing encoders as default inference endpoints" in {
    val yaml =
      """
        |models:
        |  default:
        |    type: lambdamart
        |    weights: {}
        |    backend:
        |      type: xgboost
        |    features:
        |    - foo
        |features:
        |  - type: field_match
        |    name: foo
        |    rankingField: ranking.query
        |    itemField: item.title
        |    distance: cosine
        |    method:
        |      type: bi-encoder
        |      dim: 384
        |      model: metarank/all-MiniLM-L6-v2""".stripMargin
    val parsed = parse(yaml).flatMap(_.as[Config])
    parsed.map(_.inference) shouldBe Right(
      Map(
        "foo" -> BiEncoderConfig(
          model = Some(HuggingFaceHandle("metarank", "all-MiniLM-L6-v2")),
          dim = 384
        )
      )
    )
  }
}
