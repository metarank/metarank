package me.dfdx.metarank

import java.util.concurrent.Executors

import cats.effect.IO
import me.dfdx.metarank.services.{HealthcheckService, IngestService}
import cats.implicits._
import me.dfdx.metarank.config.Config
import org.http4s.HttpRoutes

import scala.concurrent.ExecutionContext

object IngestMain extends RestIOApp {
  override val executor            = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))
  override def serviceName: String = "ingester"
  override def services(config: Config): HttpRoutes[IO] = {
    val aggs = config.featurespace.map(fs => fs.id -> Aggregations.fromConfig(fs)).toMap
    aggs.foldLeft(HealthcheckService.route) { case (route, (fs, agg)) =>
      route.combineK(IngestService(fs, agg).route)
    }
  }

}
