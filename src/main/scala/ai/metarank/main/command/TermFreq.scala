package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatcherType.BM25MatcherType
import ai.metarank.fstore.TrainStore
import ai.metarank.main.CliArgs.{ExportArgs, TermFreqArgs}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource

object TermFreq extends Logging {
  def run(
      conf: Config,
      mapping: FeatureMapping,
      args: TermFreqArgs
  ): IO[Unit] = for {
    itemFields <- IO.blocking(conf.features.collect { case FieldMatchSchema(_, _,field, BM25MatcherType(language, _))})
  }
}
