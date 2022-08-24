package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.ModelConfig.ModelBackend
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.main.CliArgs.{ServeArgs, TrainArgs}
import ai.metarank.main.command.util.FieldStats
import ai.metarank.main.command.util.FieldStats.FieldStat
import ai.metarank.model.{ClickthroughValues, MValue, TrainResult}
import ai.metarank.model.TrainResult.{FeatureStatus, IterationStatus}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.rank.LambdaMARTModel.LambdaMARTScorer
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor}
import scala.util.Random

object Train extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: TrainArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      mapping.models.get(args.model) match {
        case Some(model: LambdaMARTModel) => train(store, model, args.model, model.conf.backend).void
        case _ => IO.raiseError(new Exception(s"model ${args.model} is not defined in config"))
      }
    })
  }

  def split(dataset: Dataset, factor: Int) = {
    val (train, test) = dataset.groups.partition(_ => Random.nextInt(100) < factor)
    (Dataset(dataset.desc, train), Dataset(dataset.desc, test))
  }

  def train(store: Persistence, model: LambdaMARTModel, name: String, backend: ModelBackend): IO[TrainResult] = for {
    clickthroughtsRaw <- store.cts.getall().compile.toList.map(_.sortBy(_.ct.ts.ts))
    clickthroughs     <- IO(clickthroughtsRaw.filter(_.ct.interactions.nonEmpty))
    _                 <- info(s"loaded ${clickthroughtsRaw.size} clickthroughs, ${clickthroughs.size} with clicks")
    queries <- IO(
      clickthroughs
        .sortBy(_.ct.ts.ts)
        .map(ct => ClickthroughQuery(ct.values, ct.ct.interactions, ct, model.weights, model.datasetDescriptor))
    )
    dataset <- IO(Dataset(model.datasetDescriptor, queries))
    (train, test) = split(dataset, 80)
    _            <- info(s"training model for train=${train.groups.size} test=${test.groups.size}")
    trainedModel <- IO(model.train(train, test))
    scorer       <- IO(LambdaMARTScorer(backend, trainedModel.bytes))
    _            <- store.models.put(Map(ModelName(name) -> scorer))
    _            <- info(s"model uploaded to store, ${trainedModel.bytes.length} bytes")
  } yield {
    val stats = FieldStats(clickthroughs)
    val result = TrainResult(
      iterations = trainedModel.iterations.map(r => IterationStatus(r.index, r.took, r.trainMetric, r.testMetric)),
      sizeBytes = trainedModel.bytes.length,
      features = trainedModel.weights.map { case (name, weight) =>
        val stat = stats.fields.getOrElse(name, FieldStat(name))
        FeatureStatus(name, weight, stat.zero, stat.nonZero, percentiles = stat.samples.percentiles())
      }.toList
    )
    result.features.sortBy(_.name).foreach(fs => logger.info(fs.asPrintString))
    result
  }
}
