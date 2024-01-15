package ai.metarank.ml.recommend.embedding

import ai.metarank.ml.Model
import ai.metarank.ml.Model.ItemScore
import ai.metarank.ml.recommend.embedding.KnnIndex.{KnnIndexReader, KnnIndexWriter}
import ai.metarank.model.Identifier
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Chunk
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.{EntityDecoder, EntityEncoder, Method, Request, Uri}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.util.UUID
import scala.concurrent.duration._

object QdrantIndex extends Logging {
  val BITSTREAM_VERSION = 1

  case class QdrantIndexReader(endpoint: Uri, client: Client[IO], ids: Map[String, UUID], idrev: Map[UUID, String])
      extends KnnIndexReader {
    override def lookup(items: List[Identifier.ItemId], n: Int): IO[List[Model.ItemScore]] = {
      val request = SearchRequest(items.flatMap(x => ids.get(x.value)), n)
      client
        .expect[SearchResponse](
          Request[IO](uri = endpoint / "points" / "recommend", method = Method.POST).withEntity(request)
        )
        .flatTap(resp => info(s"request: ${request}") *> info(s"response: ${resp}"))
        .map(response => response.result.flatMap(is => idrev.get(is.id).map(id => ItemScore(ItemId(id), is.score))))
    }

    override def save(): Array[Byte] = {
      val out    = new ByteArrayOutputStream()
      val stream = new DataOutputStream(out)
      stream.writeByte(BITSTREAM_VERSION)
      stream.writeInt(ids.size)
      ids.foreach { case (key, value) =>
        stream.writeUTF(key)
        stream.writeLong(value.getLeastSignificantBits)
        stream.writeLong(value.getMostSignificantBits)
      }
      out.toByteArray
    }
  }

  object QdrantIndexWriter extends KnnIndexWriter[QdrantIndexReader, QdrantOptions] {
    override def load(bytes: Array[Byte], options: QdrantOptions): IO[QdrantIndexReader] = for {
      clientTuple <- makeClient().allocated
      (client, close) = clientTuple
      ids      <- loadIdMap(bytes)
      endpoint <- IO.fromEither(Uri.fromString(options.endpoint))
    } yield {
      QdrantIndexReader(endpoint / "collections" / options.collection, client, ids, ids.map(kv => kv._2 -> kv._1))
    }

    private def loadIdMap(bytes: Array[Byte]): IO[Map[String, UUID]] = IO {
      val in      = new ByteArrayInputStream(bytes)
      val stream  = new DataInputStream(in)
      val version = stream.readByte()
      val size    = stream.readInt()
      val pairs = (0 until size).map(_ => {
        val key   = stream.readUTF()
        val least = stream.readLong()
        val most  = stream.readLong()
        key -> new UUID(most, least)
      })
      pairs.toMap
    }

    override def write(embeddings: EmbeddingMap, options: QdrantOptions): IO[QdrantIndexReader] = for {
      clientTuple <- makeClient().allocated
      (client, _) = clientTuple
      endpoint <- IO.fromEither(Uri.fromString(options.endpoint))
      uri = endpoint / "collections" / options.collection
      exists <- collectionExists(client, uri)
      _      <- IO.whenA(!exists)(createCollection(client, uri, options.dim, options.distance).void)
      ids <- fs2.Stream
        .chunk[IO, String](Chunk.array(embeddings.ids))
        .zip(fs2.Stream.chunk(Chunk.array(embeddings.embeddings)))
        .map(x => Embedding(x._1, x._2))
        .groupWithin(128, 1.second)
        .evalMap(batch => putBatch(client, uri, batch.toList))
        .flatMap(list => fs2.Stream(list: _*))
        .compile
        .toList
      _ <- info(s"uploaded ${ids.size} vectors")
    } yield {
      QdrantIndexReader(uri, client, ids.toMap, ids.map(x => x._2 -> x._1).toMap)
    }

    def makeClient() = EmberClientBuilder
      .default[IO]
      .withTimeout(10.second)
      .build

    def createCollection(client: Client[IO], uri: Uri, dim: Int, dist: String): IO[QdrantResponse] = {
      info(s"creating collection $uri") *> client.expect[QdrantResponse](
        Request[IO](method = Method.PUT, uri = uri.withQueryParam("wait", "true"))
          .withEntity(CreateCollectionRequest(CreateCollectionVectors(dim, dist)))
      )
    }

    def collectionExists(client: Client[IO], collection: Uri): IO[Boolean] =
      client.get(collection)(response =>
        response.status.code match {
          case 200   => info(s"collection $collection exists") *> IO(true)
          case other => info(s"collection $collection is missing, status=$other") *> IO(false)
        }
      )

    def putBatch(client: Client[IO], collection: Uri, batch: List[Embedding]): IO[List[(String, UUID)]] =
      client
        .expect[QdrantResponse](
          Request[IO](method = Method.PUT, uri = (collection / "points").withQueryParam("wait", "true"))
            .withEntity(
              QdrantPointsRequest(batch.map(e => QdrantPoint(UUID.nameUUIDFromBytes(e.id.getBytes()), e.vector)))
            )
        )
        .map(r => batch.map(e => e.id -> UUID.nameUUIDFromBytes(e.id.getBytes())))
        .flatTap(batch => info(s"wrote batch of ${batch.size} items"))

  }

  case class CreateCollectionRequest(vectors: CreateCollectionVectors)
  case class CreateCollectionVectors(size: Int, distance: String)
  implicit val vectorsCodec: Codec[CreateCollectionVectors]           = deriveCodec
  implicit val createCodec: Codec[CreateCollectionRequest]            = deriveCodec
  implicit val createJson: EntityEncoder[IO, CreateCollectionRequest] = jsonEncoderOf[CreateCollectionRequest]

  case class QdrantOptions(endpoint: String, collection: String, dim: Int, distance: String)

  case class QdrantResponse(status: String)
  implicit val responseCodec: Codec[QdrantResponse]            = deriveCodec
  implicit val responseJson: EntityDecoder[IO, QdrantResponse] = jsonOf[IO, QdrantResponse]

  case class QdrantPointsRequest(points: List[QdrantPoint])
  case class QdrantPoint(id: UUID, vector: Array[Double])
  implicit val pointCodec: Codec[QdrantPoint]                = deriveCodec
  implicit val pointRequestCodec: Codec[QdrantPointsRequest] = deriveCodec

  implicit val pointRequestJson: EntityEncoder[IO, QdrantPointsRequest] = jsonEncoderOf[QdrantPointsRequest]

  case class SearchRequest(positive: List[UUID], limit: Int)
  implicit val searchCodec: Codec[SearchRequest]            = deriveCodec
  implicit val searchJson: EntityEncoder[IO, SearchRequest] = jsonEncoderOf[SearchRequest]

  case class SearchResponse(result: List[IdScore], status: String)
  case class IdScore(id: UUID, score: Double)
  implicit val idscoreCodec: Codec[IdScore]                          = deriveCodec
  implicit val searchResponseCodec: Codec[SearchResponse]            = deriveCodec
  implicit val searchResponseJson: EntityDecoder[IO, SearchResponse] = jsonOf[IO, SearchResponse]
}
