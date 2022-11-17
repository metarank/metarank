package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.ModelConfig.ModelBackend
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.main.CliArgs.{ServeArgs, TrainArgs}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.Split
import ai.metarank.main.command.util.{FieldStats, StreamResource}
import ai.metarank.main.command.util.FieldStats.FieldStat
import ai.metarank.model.{ClickthroughValues, MValue, QueryMetadata, TrainResult}
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
      ctsResource: Resource[IO, ClickthroughStore],
      mapping: FeatureMapping,
      args: TrainArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      ctsResource.use(cts => {
        for {
          models <- args.model match {
            case Some(modelName) =>
              IO.fromOption(mapping.models.get(modelName).map(m => modelName -> m))(
                new Exception(
                  s"Model '$modelName' is not defined in config (possible values: ${mapping.models.keys.toList.mkString(", ")})"
                )
              ).map(x => Map(x._1 -> x._2))
            case None => info(s"Training all models: ${mapping.models.keys.toList}") *> IO.pure(mapping.models)
          }
          _ <- models.toList
            .map {
              case (name, model: LambdaMARTModel) =>
                store match {
                  case MemPersistence(_) =>
                    IO.raiseError(
                      new Exception(
                        """=======
                          |You're using an in-mem persistence and invoked a train sub-command.
                          |In-mem persistence is not actually persisting anything between metarank invocations,
                          |so it has zero saved click-through records for model training.
                          |
                          |You probably need to enable redis persistence in the config file, or use
                          |a standalone mode (which imports data and trains ML model within a single
                          |JVM process)
                          |=======""".stripMargin
                      )
                    )
                  case _ =>
                    train(store, cts, model, name, model.conf.backend, args.split).void
                }

              case _ => IO.raiseError(new Exception(s"model ${args.model} is not defined in config"))
            }
            .sequence
            .void
        } yield {
          logger.info("Training finished")
        }
      })
    })
  }

  def train(
      store: Persistence,
      cts: ClickthroughStore,
      model: LambdaMARTModel,
      name: String,
      backend: ModelBackend,
      splitter: SplitStrategy
  ): IO[TrainResult] = for {
    split        <- loadDataset(cts, model, splitter)
    _            <- info(s"training model for train=${split.train.groups.size} test=${split.test.groups.size}")
    trainedModel <- IO(model.train(split.train, split.test))
    scorer       <- IO(LambdaMARTScorer(backend, trainedModel.bytes))
    _            <- store.models.put(Map(ModelName(name) -> scorer))
    _            <- store.sync
    _            <- info(s"model uploaded to store, ${trainedModel.bytes.length} bytes")
  } yield {
    val result = TrainResult(
      iterations = trainedModel.iterations.map(r => IterationStatus(r.index, r.took, r.trainMetric, r.testMetric)),
      sizeBytes = trainedModel.bytes.length,
      features = trainedModel.weights.map { case (name, weight) =>
        FeatureStatus(name, weight)
      }.toList
    )
    result.features.sortBy(_.name).foreach(fs => logger.info(fs.asPrintString))
    result
  }

  def loadDataset(
      cts: ClickthroughStore,
      model: LambdaMARTModel,
      splitter: SplitStrategy,
      sample: Double = 1.0
  ): IO[Split] = for {
    clickthroughs <- cts
      .getall()
      .filter(_.ct.interactions.nonEmpty)
      // .filter(_ => Random.nextDouble() <= sample)
      .map(ct =>
        QueryMetadata(
          query = ClickthroughQuery(ct.values, ct.ct.interactions, ct, model.weights, model.datasetDescriptor),
          ts = ct.ct.ts,
          user = ct.ct.user
        )
      )
      .compile
      .toList
    _ <- info(s"loaded ${clickthroughs.size} clickthroughs")
    _ <- IO.whenA(clickthroughs.isEmpty)(
      IO.raiseError(
        new Exception(
          """
            |Cannot train model on an empty dataset. Maybe you forgot to do 'metarank import'?
            |Possible options:
            |1. Dataset in-consistency. To check for consistency issues, run 'metarank validate --config conf.yml --data data.jsonl.gz',
            |2. You've used an in-memory persistence for import, and after restart the data was lost. Maybe try setting 'state.type=redis'?
            |""".stripMargin
        )
      )
    )
    split <- splitter.split(model.datasetDescriptor, clickthroughs)
    _ <- split match {
      case Split(train, _) if train.groups.isEmpty =>
        IO.raiseError(new Exception(s"Train dataset is empty (with ${clickthroughs.size} total click-through events)"))
      case Split(_, test) if test.groups.isEmpty =>
        IO.raiseError(new Exception(s"Test dataset is empty (with ${clickthroughs.size} total click-through events)"))
      case Split(train, test) if (train.groups.size < 10) || (test.groups.size < 10) =>
        IO.raiseError(
          new Exception(s"""Train/test datasets are too small: train=${train.groups.size}, test=${test.groups.size}.
                           |It is not possible to train the ML model on such a small dataset.""".stripMargin)
        )
      case Split(train, test) =>
        info(s"Train/Test split finished: ${train.groups.size}/${test.groups.size} click-through events")
    }
  } yield {
    split
  }
}
