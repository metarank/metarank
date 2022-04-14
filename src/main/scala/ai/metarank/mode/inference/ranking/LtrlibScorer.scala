package ai.metarank.mode.inference.ranking
import ai.metarank.util.Logging
import cats.effect.IO
import io.github.metarank.ltrlib.booster.{Booster, LightGBMBooster, XGBoostBooster}
import io.github.metarank.ltrlib.model.Query
import org.apache.commons.codec.binary.Base64

import java.nio.charset.StandardCharsets

case class LtrlibScorer(booster: Booster[_]) extends RankScorer {
  override def score(input: Query): Array[Double] = {
    val features = new Array[Double](input.rows * input.columns)
    var pos      = 0
    for {
      rowIndex <- 0 until input.rows
      row = input.getRow(rowIndex)
    } {
      System.arraycopy(row, 0, features, pos, row.length)
      pos += row.length
    }
    booster.predictMat(features, input.rows, input.columns)
  }
}

object LtrlibScorer extends Logging {
  def fromBytes(model: Array[Byte]): IO[LtrlibScorer] = {
    val first4 = new String(model.take(4), StandardCharsets.US_ASCII)

    first4 match {
      case "tree" => IO(logger.info("loaded LightGBM model")) *> IO(LtrlibScorer(LightGBMBooster(model)))
      case "binf" => IO(logger.info("loaded XGBoost model")) *> IO(LtrlibScorer(XGBoostBooster(model)))
      case "Ymlu" =>
        IO(logger.info("loaded base64-encoded XGBoost model")) *> IO(
          LtrlibScorer(XGBoostBooster(Base64.decodeBase64(model)))
        )
      case _ => IO.raiseError(new IllegalArgumentException("cannot detect model type"))
    }

  }
}
