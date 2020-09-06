package me.dfdx.metarank

import java.util.concurrent.Executors

import me.dfdx.metarank.services.{HealthcheckService, IngestService}
import cats.implicits._

import scala.concurrent.ExecutionContext

object IngestMain extends RestIOApp {
  override val executor            = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))
  override def serviceName: String = "ingester"
  override val services            = HealthcheckService.route <+> IngestService.route

}
