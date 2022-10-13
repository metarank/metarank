package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.ModelConfig.ModelBackend
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.main.CliArgs.{ServeArgs, TrainArgs}
import ai.metarank.main.command.util.{FieldStats, StreamResource}
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
import cats.implicits._
import io.github.metarank.ltrlib.output.CSVOutputFormat

import java.io.FileOutputStream
import java.nio.file.{Files, Path, Paths}

object Train extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: TrainArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      val models = args.model match {
        case Some(m) => mapping.models.filter(_._1 == m)
        case None    => mapping.models
      }
      models.toList
        .map {
          case (name, model: LambdaMARTModel) =>
            store match {
              case MemPersistence(_) =>
                IO.raiseError(
                  new Exception("""=======
                                  |You're using an in-mem persistence and invoked a train sub-command.
                                  |In-mem persistence is not actually persisting anything between metarank invocations,
                                  |so it has zero saved click-through records for model training.
                                  |
                                  |You probably need to enable redis persistence in the config file, or use
                                  |a standalone mode (which imports data and trains ML model within a single
                                  |JVM process)
                                  |=======""".stripMargin)
                )
              case _ => train(store, model, name, model.conf.backend, args.`export`).void
            }

          case _ => IO.raiseError(new Exception(s"model ${args.model} is not defined in config"))
        }
        .sequence
        .void
    })
  }

  def split(dataset: Dataset, factor: Int) = {
    val (train, test) = dataset.groups.partition(_ => Random.nextInt(100) < factor)
    (Dataset(dataset.desc, train), Dataset(dataset.desc, test))
  }

  def train(
      store: Persistence,
      model: LambdaMARTModel,
      name: String,
      backend: ModelBackend,
      `export`: Option[Path]
  ): IO[TrainResult] = for {
    clickthroughtsRaw <- store.cts.getall().compile.toList.map(_.sortBy(_.ct.ts.ts))
    clickthroughs     <- IO(clickthroughtsRaw.filter(_.ct.interactions.nonEmpty))
    _                 <- info(s"loaded ${clickthroughtsRaw.size} clickthroughs, ${clickthroughs.size} with clicks")
    queries <- IO(
      clickthroughs
        .sortBy(_.ct.ts.ts)
        .map(ct => ClickthroughQuery(ct.values, ct.ct.interactions, ct, model.weights, model.datasetDescriptor))
    )
    dataset <- IO(Dataset(model.datasetDescriptor, queries))
    _ <- dataset.groups match {
      case Nil => IO.raiseError(new Exception("Cannot train model: empty dataset"))
      case _   => info(s"generated training dataset: ${dataset.groups.size} groups, ${dataset.desc.dim} dims")
    }
    (train, test) = split(dataset, 80)
    _            <- info(s"training model for train=${train.groups.size} test=${test.groups.size}")
    trainedModel <- IO(model.train(train, test))
    scorer       <- IO(LambdaMARTScorer(backend, trainedModel.bytes))
    _            <- store.models.put(Map(ModelName(name) -> scorer))
    _            <- store.sync
    _            <- info(s"model uploaded to store, ${trainedModel.bytes.length} bytes")
    _ <- `export` match {
      case None => info("not exporting dataset files, set --export flag to enable.")
      case Some(path) =>
        for {
          _ <- StreamResource.of(Paths.get(path.toString + "/train.csv")).use(s => IO(CSVOutputFormat.write(s, train)))
          _ <- StreamResource.of(Paths.get(path.toString + "/test.csv")).use(s => IO(CSVOutputFormat.write(s, test)))
        } yield {
          logger.info("export done")
        }

    }
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
