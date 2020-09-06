package me.dfdx.metarank

import java.util.concurrent.Executors

import me.dfdx.metarank.services.{HealthcheckService, RankService}
import cats.implicits._

import scala.concurrent.ExecutionContext

object ApiMain extends RestIOApp {
  override val executor            = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(4))
  override def serviceName: String = "API"
  override val services            = HealthcheckService.route <+> RankService.route

}
