package ai.metarank.util.analytics

import ai.metarank.main.Constants
import ai.metarank.model.AnalyticsPayload
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Chunk
import io.circe.Printer
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.{Entity, Method, Request, Uri}
import scala.concurrent.duration._
import io.circe.syntax._

object AnalyticsReporter extends Logging {
  val jsonFormat = Printer.noSpaces.copy(dropNullValues = true)

  def ping(payload: AnalyticsPayload): IO[Unit] = {
    val clientResource = BlazeClientBuilder[IO]
      .withRequestTimeout(10.second)
      .withConnectTimeout(10.second)
      .withScheduler(new TickWheelExecutor(tick = 50.millis))
      .resource
    clientResource
      .use(http =>
        for {
          start <- IO(System.currentTimeMillis())
          uri   <- IO.fromEither(Uri.fromString(s"${Constants.ANALYTICS_ENDPOINT}/metarank-tracker"))
          request = Request[IO](
            method = Method.POST,
            uri = uri,
            entity = Entity.strict(Chunk.array(jsonFormat.print(payload.asJson).getBytes()))
          )
          _   <- http.expect[Unit](request)
          end <- IO(System.currentTimeMillis())
          _ <- info(
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
