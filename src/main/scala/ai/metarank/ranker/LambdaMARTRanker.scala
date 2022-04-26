package ai.metarank.ranker

import ai.metarank.config.Config.ModelConfig
import ai.metarank.config.Config.ModelConfig.LambdaMARTConfig
import ai.metarank.feature.BaseFeature
import ai.metarank.feature.BaseFeature.{ItemFeature, RankingFeature}
import ai.metarank.mode.train.Train.logger
import ai.metarank.mode.train.TrainCmdline
import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.{Clickthrough, Event, Ranker}
import ai.metarank.ranker.LambdaMARTRanker.Fillrate
import cats.data.NonEmptyMap
import io.findify.featury.model.{FeatureValue, Schema}
import io.github.metarank.ltrlib.booster.Booster.BoosterOptions
import io.github.metarank.ltrlib.booster.{LightGBMBooster, XGBoostBooster}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature}
import io.github.metarank.ltrlib.ranking.pairwise.LambdaMART

case class LambdaMARTRanker(
    conf: LambdaMARTConfig,
    features: List[BaseFeature],
    datasetDescriptor: DatasetDescriptor,
    weights: NonEmptyMap[String, Double]
) extends Ranker {
  override def featureValues(
      ranking: Event.RankingEvent,
      source: List[FeatureValue],
      interactions: List[Event.InteractionEvent]
  ): List[Clickthrough.ItemValues] = {
    val state = source.map(fv => fv.key -> fv).toMap

    val itemFeatures: List[ItemFeature] = features.collect { case feature: ItemFeature =>
      feature
    }

    val rankingFeatures = features.collect { case feature: RankingFeature =>
      feature
    }

    val rankingValues = rankingFeatures.map(_.value(ranking, state))

    val itemValuesMatrix = itemFeatures
      .map(feature => {
        val values = feature.values(ranking, state)
        values.foreach { value =>
          if (feature.dim != value.dim)
            throw new IllegalStateException(s"for ${feature.schema} dim mismatch: ${feature.dim} != ${value.dim}")
        }
        values
      })
      .transpose

    val itemScores = for {
      (item, itemValues) <- ranking.items.toList.zip(itemValuesMatrix)
    } yield {
      val weight = interactions.find(_.item == item.id).map(e => weights.lookup(e.`type`).getOrElse(1.0)).getOrElse(0.0)
      ItemValues(item.id, weight, rankingValues ++ itemValues)
    }
    itemScores
  }

  override def train(train: Dataset, test: Dataset, iterations: Int): Option[Array[Byte]] = {
    val opts = BoosterOptions(trees = iterations)
    val booster = conf.backend match {
      case ModelConfig.LightGBMBackend => LambdaMART(train, opts, LightGBMBooster, Some(test))
      case ModelConfig.XGBoostBackend  => LambdaMART(train, opts, XGBoostBooster, Some(test))
    }
    val model = booster.fit()
    logger.info("Feature stats: ")
    fieldStats(train, model.weights()).foreach(field => logger.info(field.print()))
    Some(model.save())
  }

  private def fieldStats(ds: Dataset, weights: Array[Double]) = {
    val zeroes    = new Array[Int](ds.desc.dim)
    val nonzeroes = new Array[Int](ds.desc.dim)
    for {
      group <- ds.groups
      i     <- 0 until group.rows
      row = group.getRow(i)
      (value, j) <- row.zipWithIndex
    } {
      if (value == 0.0) {
        zeroes(j) += 1
      } else {
        nonzeroes(j) += 1
      }
    }
    val expanded = ds.desc.features.flatMap {
      case Feature.SingularFeature(name)     => List(name)
      case Feature.VectorFeature(name, size) => (0 until size).map(i => s"${name}_$i")
    }
    for {
      (feature, index) <- expanded.zipWithIndex
    } yield {
      Fillrate(feature, zeroes(index), nonzeroes(index), weights(index))
    }
  }
}

object LambdaMARTRanker {
  case class Fillrate(name: String, zeroes: Int, nonzeroes: Int, weight: Double) {
    def print() = s"$name: zero=$zeroes nonzero=$nonzeroes weight=$weight"
  }
}
