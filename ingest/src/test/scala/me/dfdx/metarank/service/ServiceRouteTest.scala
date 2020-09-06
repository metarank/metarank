package me.dfdx.metarank.service

import cats.effect.IO
import io.circe.Encoder
import me.dfdx.metarank.services.IngestService
import org.http4s.{HttpRoutes, Method, Request, Uri}
import io.circe.syntax._

trait ServiceRouteTest {
  def get(route: HttpRoutes[IO], path: String) = {
    val request = Request[IO](uri = Uri.unsafeFromString(path))
    route.run(request).value.unsafeRunSync()
  }

  def post[T](route: HttpRoutes[IO], event: T, path: String)(implicit encoder: Encoder[T]) = {
    val request = Request[IO](uri = Uri.unsafeFromString(path), method = Method.POST, body = makeBody(event))
    route.run(request).value.unsafeRunSync()
  }

  def makeBody[T](event: T)(implicit encoder: Encoder[T]): fs2.Stream[IO, Byte] = {
    val bytes = event.asJson.noSpaces.getBytes()
    fs2.Stream(bytes: _*)
  }

}
