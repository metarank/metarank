package ai.metarank.mode

import ai.metarank.config.MPath
import ai.metarank.config.MPath.LocalPath
import better.files.File
import cats.effect.{IO, Resource}
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3Client, AmazonS3ClientBuilder}
import org.apache.commons.io.IOUtils

import java.io.ByteArrayInputStream

object FileLoader {
  def write(path: MPath, env: Map[String, String], bytes: Array[Byte]) = path match {
    case MPath.S3Path(bucket, path) =>
      s3client(env).use(client =>
        IO {
          val request = new PutObjectRequest(bucket, path, new ByteArrayInputStream(bytes), new ObjectMetadata())
          client.putObject(request)
        }
      )
    case local: LocalPath =>
      IO(local.file.writeByteArray(bytes))
  }
  def read(path: MPath, env: Map[String, String]): IO[Array[Byte]] = path match {
    case MPath.S3Path(bucket, prefix) =>
      s3client(env).use(client =>
        IO {
          val request  = new GetObjectRequest(bucket, prefix)
          val response = client.getObject(request)
          val bytes    = IOUtils.readFully(response.getObjectContent, Int.MaxValue)
          bytes
        }
      )
    case LocalPath(local) => IO { File(local).byteArray }
  }

  def s3client(env: Map[String, String]) = {
    Resource.make(for {
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
      endpoint match {
        case Some(customEndpoint) =>
          builder.withEndpointConfiguration(new EndpointConfiguration(customEndpoint, "us-east-1")).build()
        case None =>
          builder.build()
      }
    })(client => IO(client.shutdown()))
  }
}
