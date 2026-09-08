package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, ModelConfig}
import ai.metarank.fstore.{TrainStore, Persistence}
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.main.CliArgs.TrainArgs
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTModel, LambdaMARTPredictor}
import ai.metarank.model.{TrainResult, TrainValues}
import ai.metarank.model.TrainResult.FeatureStatus
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*

object Train extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      ctsResource: Resource[IO, TrainStore],
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
          _ <- store match {
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
            case _ => IO.unit
          }
          _ <- models.toList match {
            // A single model needs no buffering, so keep streaming it straight into fit
            case (_, pred) :: Nil => train(store, cts, pred).void
            case many =>
              for {
                data <- cts.getall().compile.toVector
                _    <- info(s"loaded ${data.size} train records, reused across ${many.size} models")
                _    <- many.traverse { case (_, pred) => trainShared(store, data, pred) }.void
              } yield {}
          }
        } yield {
          logger.info("Training finished")
        }
      })
    })
  }

  def train(
      store: Persistence,
      cts: TrainStore,
      predictor: Predictor[? <: ModelConfig, ?, ? <: Model[? <: Context]]
  ): IO[TrainResult] =
    fitAndStore(store, cts.getall(), predictor)

  def trainShared(
      store: Persistence,
      data: Vector[TrainValues],
      predictor: Predictor[? <: ModelConfig, ?, ? <: Model[? <: Context]]
  ): IO[TrainResult] =
    fitAndStore(store, fs2.Stream.emits(data).covary[IO], predictor)

  private def fitAndStore(
      store: Persistence,
      data: fs2.Stream[IO, TrainValues],
      predictor: Predictor[? <: ModelConfig, ?, ? <: Model[? <: Context]]
  ): IO[TrainResult] = for {
    model <- predictor.fit(data.filter(c => predictor.config.selector.accept(c)))
    weights = (model, predictor) match {
      case (m: LambdaMARTModel, p: LambdaMARTPredictor) =>
        m.weights(p.desc).map { case (name, w) => FeatureStatus(name, w) }
      case _ => Nil
    }
    _ <- store.models.put(model)
    _ <- store.sync
    _ <- info(s"model uploaded to store")
  } yield {
    weights.foreach(fs => logger.info(fs.asPrintString))
    TrainResult(weights.toList)
  }

}
