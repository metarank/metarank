package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{ImportArgs, ServeArgs}
import ai.metarank.main.command.{Import, Serve}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}
import org.apache.commons.io.IOUtils
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import scala.util.Try

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    args       <- IO.fromEither(CliArgs.parse(args)).onError(ex => IO(CliArgs))
    confString <- IO.fromTry(Try(IOUtils.toString(new FileInputStream(args.conf.toFile), StandardCharsets.UTF_8)))
    conf       <- Config.load(confString)
    mapping   <- IO(FeatureMapping.fromFeatureSchema(conf.features, conf.models))
    store = Persistence.fromConfig(mapping.schema, conf.state)
    _ <- args match {
      case a: ServeArgs  => Serve.run(conf, store, mapping, a)
      case a: ImportArgs => Import.run(conf, store, mapping, a)
    }
    _ <- info("My job is done, exiting.")
  } yield {
    ExitCode.Success
  }

}
