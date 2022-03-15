package ai.metarank.mode

import better.files.File
import cats.effect.IO
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3ClientBuilder}
import org.apache.commons.io.IOUtils

object FileLoader {
  val s3Pattern   = "s3://([a-zA-Z0-9\\-_]+)/(.*)".r
  val filePattern = "file://(.*)".r
  def loadLocal(path: String, env: Map[String, String]): IO[Array[Byte]] = path match {
    case s3Pattern(bucket, prefix) =>
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
    case filePattern(local)             => IO { File(local).byteArray }
    case other if other.startsWith("/") => IO { File(other).byteArray }
    case other                          => IO { (File.currentWorkingDirectory / other).byteArray }
  }

  def makeURL(path: String): String = path match {
    case s3 @ s3Pattern(_, _)                 => s3
    case file @ filePattern(_)                => file
    case absolute if absolute.startsWith("/") => "file://" + "absolute"
    case relative                             => "file://" + (File.currentWorkingDirectory / relative).toString
  }

}
