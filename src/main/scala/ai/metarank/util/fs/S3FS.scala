package ai.metarank.util.fs

import ai.metarank.config.MPath.S3Path
import cats.effect.{IO, Resource}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.{GetObjectRequest, ListObjectsV2Request, ObjectMetadata, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.apache.commons.io.IOUtils
import scala.jdk.CollectionConverters._
import java.io.ByteArrayInputStream

case class S3FS(client: AmazonS3) extends FS[S3Path] {
  override def read(path: S3Path): IO[Array[Byte]] = IO {
    val request  = new GetObjectRequest(path.bucket, path.path)
    val response = client.getObject(request)
    val bytes    = IOUtils.readFully(response.getObjectContent, Int.MaxValue)
    bytes
  }
  override def write(path: S3Path, bytes: Array[Byte]): IO[Unit] = IO {
    val request = new PutObjectRequest(path.bucket, path.path, new ByteArrayInputStream(bytes), new ObjectMetadata())
    client.putObject(request)
  }

  override def listRecursive(path: S3Path): IO[List[S3Path]] = IO {
    val request = new ListObjectsV2Request()
    request.setBucketName(path.bucket)
    request.setPrefix(path.path)
    val response = client.listObjectsV2(request)
    response.getObjectSummaries.asScala.toList.map(sum => S3Path(path.bucket, sum.getKey))
  }
}

object S3FS {
  def create(env: Map[String, String]): Either[Throwable, S3FS] = for {
    key <- env
      .get("AWS_ACCESS_KEY_ID")
      .toRight(new IllegalArgumentException("AWS_ACCESS_KEY_ID not defined for s3:// scheme"))
    secret <- env
      .get("AWS_SECRET_ACCESS_KEY")
      .toRight(new IllegalArgumentException("AWS_SECRET_ACCESS_KEY not defined for s3:// scheme"))
    endpoint = env.get("AWS_S3_ENDPOINT_URL")
  } yield {
    val creds   = new AWSStaticCredentialsProvider(new BasicAWSCredentials(key, secret))
    val builder = AmazonS3ClientBuilder.standard().withCredentials(creds)
    val client = endpoint match {
      case Some(customEndpoint) =>
        builder.withEndpointConfiguration(new EndpointConfiguration(customEndpoint, "us-east-1")).build()
      case None =>
        builder.build()
    }
    S3FS(client)

  }
  def resource(env: Map[String, String]) = Resource.make(IO.fromEither(create(env)))(fs => IO(fs.client.shutdown()))

}
