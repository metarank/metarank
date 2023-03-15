package ai.metarank.ml.recommend

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

sealed trait KnnConfig

object KnnConfig {
  case class HnswConfig(m: Int = 32, ef: Int = 200)                                          extends KnnConfig
  case class QdrantConfig(endpoint: String, collection: String, size: Int, distance: String) extends KnnConfig

  implicit val hnswDecoder: Decoder[HnswConfig] = Decoder.instance(c =>
    for {
      m  <- c.downField("m").as[Option[Int]]
      ef <- c.downField("ef").as[Option[Int]]
    } yield {
      HnswConfig(m.getOrElse(32), ef.getOrElse(200))
    }
  )
  implicit val hnswEncoder: Encoder[HnswConfig] = deriveEncoder

  implicit val qdrantDecoder: Decoder[QdrantConfig] = deriveDecoder
  implicit val qdrantEncoder: Encoder[QdrantConfig] = deriveEncoder

  implicit val knnDecoder: Decoder[KnnConfig] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(value)     => Left(value)
      case Right("hnsw")   => hnswDecoder.tryDecode(c)
      case Right("qdrant") => qdrantDecoder.tryDecode(c)
      case Right(other)    => Left(DecodingFailure(s"knn index type '$other' is not supported", c.history))
    }
  )

  implicit val knnEncoder: Encoder[KnnConfig] = Encoder.instance {
    case c: HnswConfig   => hnswEncoder(c).deepMerge(withType("hnsw"))
    case c: QdrantConfig => qdrantEncoder(c).deepMerge(withType("qdrant"))
  }

  def withType(tpe: String) = Json.fromJsonObject(JsonObject.fromIterable(List("type" -> Json.fromString(tpe))))
}
