package ai.metarank.api.routes

import cats.effect.IO
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import fs2.{Chunk, Stream}
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

case class MetricsApi(registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry) {

  val routes = HttpRoutes.of[IO] { case GET -> Root / "metrics" =>
    Ok(writeMetrics())
  }

  def writeMetrics(): Stream[IO, Byte] = {
    val stream = new ByteArrayOutputStream()
    PrometheusTextFormatWriter.create().write(stream, registry.scrape())
    Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(stream.toByteArray)))
  }
}

object MetricsApi {
  def create(): IO[MetricsApi] = IO {
    MetricsApi()
  }
}
