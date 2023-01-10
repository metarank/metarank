package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, CoreConfig}
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.config.StateStoreConfig.FileStateConfig
import ai.metarank.config.StateStoreConfig.FileStateConfig.MapDBBackend
import ai.metarank.flow.MetarankFlow.ProcessResult
import ai.metarank.flow.{CheckOrderingPipe, ClickthroughJoinBuffer, MetarankFlow}
import ai.metarank.fstore.file.FilePersistence
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.fstore.transfer.FileRedisTransfer
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.main.CliArgs.ImportArgs
import ai.metarank.model.{Event, Schema, Timestamp}
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import ai.metarank.validate.EventValidation.ValidationError
import ai.metarank.validate.checks.EventOrderValidation.EventOrderError
import cats.effect.IO
import cats.effect.kernel.Resource

import java.nio.file.Files

object Import extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      ctsResource: Resource[IO, ClickthroughStore],
      mapping: FeatureMapping,
      args: ImportArgs
  ): IO[Unit] = {
    storeResource.use(store =>
      ctsResource.use(cts => {
        for {
          buffer <- IO(ClickthroughJoinBuffer(conf.core.clickthrough, store.values, cts, mapping))
          result <- slurp(store, mapping, args, conf, buffer)
          _      <- info(s"import done, flushing clickthrough queue of size=${buffer.queue.size()}")
          _      <- buffer.flushAll()
          _      <- store.sync
          _      <- IO(System.gc())
          _      <- info(s"Imported ${result.events} in ${result.tookMillis}ms, generated ${result.updates} updates")
        } yield {}
      })
    )
  }

  def slurp(
      store: Persistence,
      mapping: FeatureMapping,
      args: ImportArgs,
      conf: Config,
      buffer: ClickthroughJoinBuffer
  ): IO[ProcessResult] = {
    val stream = FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format, args.sort)).stream
    for {
      errors       <- validated(conf, stream, args.validation)
      sortedStream <- sorted(stream, errors)
      result       <- slurp(sortedStream, store, mapping, buffer)
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
    } else {
      stream.through(CheckOrderingPipe.process).chunkN(1024).unchunks
    }
  }

  def slurp(
      source: fs2.Stream[IO, Event],
      store: Persistence,
      mapping: FeatureMapping,
      buffer: ClickthroughJoinBuffer
  ): IO[ProcessResult] = {
    store match {
      case redis: RedisPersistence =>
        for {
          dir <- IO(Files.createTempDirectory("metarank-rocksdb-temp"))
          _   <- info(s"using local disk cache for redis persistence: $dir")
          result <- FilePersistence
            .create(FileStateConfig(dir.toString, backend = MapDBBackend), mapping.schema)
            .use(cache =>
              for {
                buf2 <- IO(buffer.copy(values = cache.values))
                r2   <- MetarankFlow.process(cache, source, mapping, buf2).flatTap(_ => cache.sync)
                _    <- info("local import done, uploading data to redis")
                _    <- FileRedisTransfer.copy(cache, redis)
                _    <- store.sync
              } yield {
                r2
              }
            )
        } yield {
          result
        }
      case other => MetarankFlow.process(store, source, mapping, buffer).flatTap(_ => store.sync)
    }
  }

}
