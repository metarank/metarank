package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.flow.MetarankFlow.ProcessResult
import ai.metarank.flow.MetarankFlow
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ImportArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import ai.metarank.validate.EventValidation.ValidationError
import ai.metarank.validate.checks.EventOrderValidation.EventOrderError
import cats.effect.IO
import cats.effect.kernel.Resource

object Import extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: ImportArgs
  ): IO[Unit] = {
    storeResource.use(store =>
      for {
        result <- slurp(store, mapping, args, conf)
        _      <- info(s"Imported ${result.events} in ${result.tookMillis}ms, generated ${result.updates} updates")
      } yield {}
    )
  }

  def slurp(store: Persistence, mapping: FeatureMapping, args: ImportArgs, conf: Config): IO[ProcessResult] = {
    val stream = FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream
    for {
      errors       <- validated(conf, stream, args.validation)
      sortedStream <- sorted(stream, errors)
      result       <- slurp(sortedStream, store, mapping)
    } yield {
      result
    }
  }

  def validated(conf: Config, stream: fs2.Stream[IO, Event], flag: Boolean): IO[List[ValidationError]] =
    if (flag) Validate.validate(conf, stream) else IO.pure(Nil)

  def sorted(stream: fs2.Stream[IO, Event], errors: List[ValidationError]): IO[fs2.Stream[IO, Event]] = IO {
    val shouldSort = errors.collectFirst { case EventOrderError(_) => }.isDefined
    if (shouldSort) {
      logger.warn("Dataset seems not to be sorted by timestamp, doing in-memory sort")
      fs2.Stream.evalSeq(stream.compile.toList.map(_.sortBy(_.timestamp.ts))).chunkN(1024).unchunks
    } else stream
  }

  def slurp(source: fs2.Stream[IO, Event], store: Persistence, mapping: FeatureMapping): IO[ProcessResult] = {
    MetarankFlow.process(store, source, mapping)
  }

}
