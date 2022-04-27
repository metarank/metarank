package ai.metarank.mode

import ai.metarank.config.MPath
import ai.metarank.config.MPath.LocalPath
import better.files.File
import cats.effect.IO
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3ClientBuilder}
import org.apache.commons.io.IOUtils

object FileLoader {
  def load(path: MPath, env: Map[String, String]): IO[Array[Byte]] = path match {
    case MPath.S3Path(bucket, prefix) =>
      for {
        key <- IO.fromOption(env.get("AWS_ACCESS_KEY_ID"))(
          new IllegalArgumentException("AWS_ACCESS_KEY_ID not defined for s3:// scheme")
        )
        secret <- IO.fromOption(env.get("AWS_SECRET_ACCESS_KEY"))(
          new IllegalArgumentException("AWS_SECRET_ACCESS_KEY not defined for s3:// scheme")
        )
        endpoint <- IO { env.get("AWS_S3_ENDPOINT_URL") }
      } yield {
        val creds   = new AWSStaticCredentialsProvider(new BasicAWSCredentials(key, secret))
        val builder = AmazonS3ClientBuilder.standard().withCredentials(creds)
        val client = endpoint match {
          case Some(customEndpoint) =>
            builder.withEndpointConfiguration(new EndpointConfiguration(customEndpoint, "us-east-1")).build()
          case None =>
            builder.build()
        }
        val request  = new GetObjectRequest(bucket, prefix)
        val response = client.getObject(request)
        val bytes    = IOUtils.readFully(response.getObjectContent, Int.MaxValue)
        client.shutdown()
        bytes
      }
    case LocalPath(local) => IO { File(local).byteArray }
  }
}
