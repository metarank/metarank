package ai.metarank.mode.k8s

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.CliApp
import ai.metarank.mode.k8s.JobResource.{BootstrapJobResource, UpdateJobResource, UploadJobResource}
import cats.effect.{ExitCode, IO}
import io.circe.yaml.printer.print
import io.circe.syntax._

object K8sGenerator extends CliApp {
  override def usage: String =
    s"""Usage: metarank k8s-manifest <config path> <job name> 
       |Possible jobs:
       |  - bootstrap
       |  - update
       |  - upload
       |""".stripMargin
  override def run(
      args: List[String],
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping
  ): IO[ExitCode] = args match {
    case _ :: "bootstrap" :: Nil =>
      val job = BootstrapJobResource(config.bootstrap.workdir, config.bootstrap.parallelism.getOrElse(2))
      IO(println(print(job.asJson))) *> IO.pure(ExitCode.Success)
    case _ :: "update" :: Nil =>
      val job = UpdateJobResource(config.bootstrap.workdir, config.inference.parallelism.getOrElse(2))
      IO(println(print(job.asJson))) *> IO.pure(ExitCode.Success)
    case _ :: "upload" :: Nil =>
      val job = UploadJobResource(config.bootstrap.workdir, config.bootstrap.parallelism.getOrElse(2))
      IO(println(print(job.asJson))) *> IO.pure(ExitCode.Success)
    case other => IO.raiseError(new Exception(s"wrong args: $other"))
  }
}
