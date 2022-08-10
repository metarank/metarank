package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{ImportArgs, ServeArgs}
import ai.metarank.main.api.ApiResource
import ai.metarank.model.{Env, Event, Schema}
import ai.metarank.source.ModelCache
import ai.metarank.util.Logging
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import org.apache.commons.io.IOUtils

import scala.concurrent.duration._
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import scala.util.Try

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    args       <- IO.fromEither(CliArgs.parse(args)).onError(ex => IO(CliArgs))
    confString <- IO.fromTry(Try(IOUtils.toString(new FileInputStream(args.conf.toFile), StandardCharsets.UTF_8)))
    conf       <- Config.load(confString)
    mappings   <- IO(conf.env.map(env => env.name -> FeatureMapping.fromEnvConfig(env)).toMap)
    store = Persistence.fromConfig(mappings.values.map(_.schema).toList, conf.state)
    _ <- args match {
      case a: ServeArgs  => doserve(conf, store, mappings, a)
      case a: ImportArgs => doimport(conf, store, mappings, a)
    }
  } yield {
    ExitCode.Success
  }

  def doserve(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mappings: Map[Env, FeatureMapping],
      args: ServeArgs
  ): IO[Unit] = {
    val flowResource = for {
      store <- storeResource
      queue <- Resource.liftK(Queue.dropping[IO, Option[Event]](1024))
      api   <- ApiResource.create(conf, store, ModelCache(store), queue)
      _     <- api.serve.compile.drain.background
      ct    = ClickthroughImpressionFlow(store, mappings)
      event = FeatureValueFlow(mappings, store)
      sink  = FeatureValueSink(store)
      flow  = Stream.fromQueueNoneTerminated(queue).through(ct.process).through(event.process).through(sink.write)
    } yield { flow }
    flowResource.use(_.compile.drain)
  }

  def doimport(
      conf: Config,
      store: Resource[IO, Persistence],
      mappings: Map[Env, FeatureMapping],
      args: ImportArgs
  ): IO[Unit] = ???
}
