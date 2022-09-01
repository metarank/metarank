package ai.metarank.util

import ai.metarank.model.AnalyticsPayload
import cats.effect.IO
import fs2.Chunk
import io.circe.Printer
import org.http4s.{Entity, Method, Request, Response, Uri}
import org.http4s.blaze.client.BlazeClientBuilder
import io.circe.syntax._
import org.http4s.blaze.util.TickWheelExecutor

import scala.concurrent.duration._

object AnalyticsReporter extends Logging {
  val endpoint   = "https://analytics.metarank.ai"
  val jsonFormat = Printer.noSpaces.copy(dropNullValues = true)

  def ping(payload: AnalyticsPayload): IO[Unit] = {
    val clientResource = BlazeClientBuilder[IO]
      .withRequestTimeout(1.second)
      .withConnectTimeout(1.second)
      .withScheduler(new TickWheelExecutor(tick = 50.millis))
      .resource
    clientResource
      .use(http =>
        for {
          _   <- info("anonymous usage reporting is enabled")
          uri <- IO.fromEither(Uri.fromString(s"$endpoint/default/metarank-tracker"))
          request = Request[IO](
            method = Method.POST,
            uri = uri,
            entity = Entity.strict(Chunk.array(jsonFormat.print(payload.asJson).getBytes()))
          )
          _ <- http.expect[Unit](request)
        } yield {}
      )
      .handleErrorWith(ex => error(s"cannot send analytics: ${ex.getMessage}"))
  }
}
