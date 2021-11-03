package me.dfdx.metarank

import java.util.concurrent.Executors
import me.dfdx.metarank.services.{HealthcheckService, IngestService}

import scala.concurrent.ExecutionContext
import cats.effect._
import org.http4s._
import cats.implicits._
import me.dfdx.metarank.config.Config

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
