package ai.metarank.mode

import ai.metarank.util.Logging
import org.apache.flink.configuration.Configuration
import scala.collection.JavaConverters._

object FlinkS3Configuration extends Logging {
  def apply(env: java.util.Map[String, String]): Configuration = apply(env.asScala.toMap)
  def apply(env: Map[String, String]): Configuration =
    (env.get("AWS_ACCESS_KEY_ID"), env.get("AWS_SECRET_ACCESS_KEY"), env.get("AWS_S3_ENDPOINT_URL")) match {
      case (Some(key), Some(secret), endpoint) =>
        val conf = new Configuration()
        conf.setString("fs.s3a.access.key", key)
        conf.setString("fs.s3.awsAccessKeyId", secret)
        conf.setString("s3.access-key", key)
        conf.setString("s3.secret-key", secret)
        endpoint.foreach(e => conf.setString("s3.endpoint", e))
        logger.info(s"loaded aws secrets: key=${stripSecrets(key)} secret=${stripSecrets(secret)} endpoint=$endpoint")
        conf
      case _ =>
        logger.info("cannot find AWS S3 access key/secret")
        new Configuration()
    }

  def stripSecrets(secret: String): String = {
    val last4 = secret.takeRight(math.min(4, secret.length))
    val xxx   = List.fill(math.max(0, secret.length - last4.length))("x").mkString("")
    xxx + last4
  }
}
