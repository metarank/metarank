package ai.metarank.util.analytics

import ai.metarank.main.Constants
import ai.metarank.model.AnalyticsPayload
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.Printer
import org.http4s.{Entity, Method, Request, Uri}

import scala.concurrent.duration._
import io.circe.syntax._
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scodec.bits.ByteVector

object AnalyticsReporter extends Logging {
  val jsonFormat = Printer.noSpaces.copy(dropNullValues = true)

  def ping(enabled: Boolean, payload: AnalyticsPayload): IO[Unit] = {
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    val clientResource = EmberClientBuilder
      .default[IO]
      .withTimeout(10.second)
      .build
    clientResource
      .use(http =>
        for {
          start <- IO(System.currentTimeMillis())
          uri   <- IO.fromEither(Uri.fromString(s"${Constants.ANALYTICS_ENDPOINT}/metarank-tracker"))
          request = Request[IO](
            method = Method.POST,
            uri = uri,
            entity = Entity.strict(ByteVector(jsonFormat.print(payload.asJson).getBytes()))
          )
          _   <- http.expect[Unit](request)
          end <- IO(System.currentTimeMillis())
          _ <- debug(
            s"anonymous usage reporting is enabled (${end - start}ms), set core.tracking.analytics=false to skip"
          )
        } yield {}
      )
      .handleErrorWith(ex => error(s"cannot send analytics: ${ex.getMessage}"))
      .background
      .allocated
      .void
  }
}
