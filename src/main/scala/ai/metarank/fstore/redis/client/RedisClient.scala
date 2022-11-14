package ai.metarank.fstore.redis.client

import ai.metarank.config.StateStoreConfig.{RedisCredentials, RedisTLS, RedisTimeouts}
import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, PipelineConfig}
import ai.metarank.fstore.redis.client.RedisClient.ScanCursor
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import cats.effect.kernel.Resource
import io.lettuce.core
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.{
  ClientOptions,
  RedisCommandTimeoutException,
  RedisConnectionException,
  RedisCredentialsProvider,
  RedisURI,
  ScanArgs,
  SocketOptions,
  SslOptions,
  SslVerifyMode,
  TimeoutOptions,
  RedisClient => LettuceClient,
  ScanCursor => LettuceCursor
}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.{ByteArrayCodec, RedisCodec, StringCodec}
import io.netty.handler.ssl.SslContextBuilder
import org.apache.commons.io.IOUtils
import reactor.core.publisher.Mono

import java.io.FileInputStream
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.cert.{CertificateFactory, X509Certificate}
import java.util.concurrent.CompletableFuture
import javax.naming.ldap.LdapName
import javax.net.ssl.{SSLContext, SSLHandshakeException, TrustManagerFactory}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.concurrent.duration._

case class RedisClient(
    lettuce: LettuceClient,
    reader: RedisAsyncCommands[String, Array[Byte]],
    readerConn: StatefulRedisConnection[String, Array[Byte]],
    writer: RedisAsyncCommands[String, Array[Byte]],
    writerConn: StatefulRedisConnection[String, Array[Byte]],
    bufferSize: Ref[IO, Int],
    conf: PipelineConfig
) extends Logging {
  // reads
  def lrange(key: String, start: Int, end: Int): IO[List[Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.lrange(key, start, end).toCompletableFuture))
      .map(_.asScala.toList)

  def get(key: String): IO[Option[Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.get(key).toCompletableFuture)).map(Option.apply)

  def mget(keys: List[String]): IO[Map[String, Array[Byte]]] = keys match {
    case Nil => IO.pure(Map.empty)
    case _ =>
      IO.fromCompletableFuture(IO(reader.mget(keys: _*).toCompletableFuture))
        .map(_.asScala.toList.flatMap(kv => kv.optional().toScala.map(v => kv.getKey -> v)).toMap)
  }

  def hgetAll(key: String): IO[Map[String, Array[Byte]]] =
    IO.fromCompletableFuture(IO(reader.hgetall(key).toCompletableFuture)).map(_.asScala.toMap)

  def ping(): IO[String] =
    IO.fromCompletableFuture(IO(reader.ping().toCompletableFuture))

  def scan(cursor: String, count: Int, pattern: String): IO[ScanCursor] =
    IO.fromCompletableFuture(
      IO(reader.scan(LettuceCursor.of(cursor), ScanArgs.Builder.limit(count).`match`(pattern)).toCompletableFuture)
    ).map(sc => ScanCursor(sc.getKeys.asScala.toList, sc.getCursor))

  // writes

  def mset(values: Map[String, Array[Byte]]): IO[Unit] = {
    if (values.nonEmpty) {
      IO(writer.mset(values.asJava).toCompletableFuture).flatMap(maybeFlush)
    } else {
      IO.unit
    }
  }

  def set(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.set(key, value).toCompletableFuture).flatMap(maybeFlush)

  def hset(key: String, values: Map[String, Array[Byte]]): IO[Unit] =
    IO(writer.hset(key, values.asJava).toCompletableFuture).flatMap(maybeFlush)

  def hdel(key: String, keys: List[String]): IO[Unit] =
    IO(writer.hdel(key, keys: _*).toCompletableFuture).flatMap(maybeFlush)

  def hincrby(key: String, subkey: String, by: Int): IO[Unit] =
    IO(writer.hincrby(key, subkey, by).toCompletableFuture).flatMap(maybeFlush)

  def incrBy(key: String, by: Int): IO[Unit] =
    IO(writer.incrby(key, by).toCompletableFuture).flatMap(maybeFlush)

  def lpush(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.lpush(key, value).toCompletableFuture).flatMap(maybeFlush)

  def lpush(key: String, values: List[Array[Byte]]): IO[Unit] =
    IO(writer.lpush(key, values: _*).toCompletableFuture).flatMap(maybeFlush)

  def ltrim(key: String, start: Int, end: Int): IO[Unit] =
    IO(writer.ltrim(key, start, end).toCompletableFuture).flatMap(maybeFlush)

  def append(key: String, value: Array[Byte]): IO[Unit] =
    IO(writer.append(key, value).toCompletableFuture).flatMap(maybeFlush)

  def maybeFlush[T](lastWrite: CompletableFuture[T]): IO[Unit] = bufferSize.updateAndGet(_ + 1).flatMap {
    case cnt if cnt >= conf.maxSize =>
      debug(s"overflow pipeline flush of $cnt writes") *> doFlush(lastWrite)
    case _ => IO.unit
  }

  def doFlush[T](last: CompletableFuture[T]): IO[Unit] = for {
    _ <- bufferSize.set(0)
    _ <- IO.fromCompletableFuture(IO {
      writerConn.flushCommands()
      last
    })
  } yield {}

  private def tick(): IO[Unit] = for {
    _ <- IO.sleep(conf.flushPeriod)
    _ <- bufferSize.getAndSet(0).flatMap {
      case 0 => IO.unit
      case cnt =>
        for {
          _ <- debug(s"scheduled pipeline flush of $cnt writes")
          _ <- IO { writerConn.flushCommands() }
        } yield {}
    }
    _ <- tick()
  } yield {}

}

object RedisClient extends Logging {
  case class ScanCursor(keys: List[String], cursor: String)
  def create(
      host: String,
      port: Int,
      db: Int,
      cache: PipelineConfig,
      auth: Option[RedisCredentials],
      tls: Option[RedisTLS],
      timeout: RedisTimeouts
  ): Resource[IO, RedisClient] = {
    Resource
      .make(for {
        buffer <- Ref.of[IO, Int](0)
        client <- createIO(host, port, db, cache, buffer, auth, tls)
        tickCancel <-
          if (cache.flushPeriod != 0.millis) {
            info(s"started flush timer every ${cache.flushPeriod}") *> client.tick().background.allocated.map(_._2)
          } else {
            info("periodic flushing disabled") *> IO.pure(IO.unit)
          }
      } yield {
        client -> tickCancel
      })(client => info("closing redis connection") *> IO(client._1.lettuce.close()) *> client._2)
      .map(_._1)
  }

  def createIO(
      host: String,
      port: Int,
      db: Int,
      conf: PipelineConfig,
      buffer: Ref[IO, Int],
      auth: Option[RedisCredentials],
      tls: Option[RedisTLS]
  ): IO[RedisClient] = for {
    lettuce <- createLettuceClient(host, port, db, tls, auth)
    read <- IO(lettuce.connect[String, Array[Byte]](RedisCodec.of(new StringCodec(), new ByteArrayCodec())))
      .handleErrorWith {
        case re: RedisConnectionException =>
          re.getCause match {
            case _: UnknownHostException =>
              warn(s"Cannot DNS resolve hostname '$host'") *> IO.raiseError(re)
            case _: SSLHandshakeException =>
              warn("Cannot setup TLS. Check the cert and try to set redis.tls.verify=off for debugging") *> IO
                .raiseError(re)
            case _: RedisCommandTimeoutException =>
              warn("Connected to Redis, but got command timeout. Maybe you're using TLS?") *> IO.raiseError(re)
            case _ => IO.raiseError(re)
          }
        case ex: Throwable => IO.raiseError(ex)
      }
    _     <- IO(read.sync().select(db))
    write <- IO(lettuce.connect[String, Array[Byte]](RedisCodec.of(new StringCodec(), new ByteArrayCodec())))
    _     <- IO(write.sync().select(db))
    _     <- IO(write.setAutoFlushCommands(false))
    _ <- info(
      s"opened read+write connection redis://$host:$port, db=$db (pipeline size: ${conf.maxSize} period: ${conf.flushPeriod})"
    )
  } yield {
    new RedisClient(
      lettuce,
      read.async(),
      read,
      write.async(),
      write,
      buffer,
      conf
    )
  }

  def createLettuceClient(
      host: String,
      port: Int,
      db: Int,
      tls: Option[RedisTLS],
      auth: Option[RedisCredentials]
  ): IO[io.lettuce.core.RedisClient] =
    IO {
      val uri =
        RedisURI.builder().withHost(host).withPort(port).withDatabase(db).withTimeout(java.time.Duration.ofSeconds(1))
      val socket        = SocketOptions.builder().connectTimeout(java.time.Duration.ofSeconds(1))
      val timeout       = TimeoutOptions.builder().fixedTimeout(java.time.Duration.ofSeconds(1)).timeoutCommands()
      val clientOptions = ClientOptions.builder().socketOptions(socket.build()).timeoutOptions(timeout.build())
      auth match {
        case None => logger.info("auth is not enabled")
        case Some(RedisCredentials(user, password)) =>
          logger.info(s"auth enabled: user=${user.map(_ => "xxxxxx")} password=xxxxxx")
          uri.withAuthentication(new RedisCredentialsProvider {
            override def resolveCredentials(): Mono[core.RedisCredentials] =
              Mono.just(core.RedisCredentials.just(user.orNull, password))
          })
      }
      tls match {
        case Some(RedisTLS(true, ca, verify)) =>
          logger.info(s"TLS enabled")
          uri.withSsl(true)
          verify match {
            case SslVerifyMode.NONE => logger.info("Skipping TLS cert verification")
            case SslVerifyMode.CA   => logger.info("Skipping hostname TLS verification (cert verification enabled)")
            case SslVerifyMode.FULL => logger.info("Cert+host TLS verification enabled")
          }
          uri.withVerifyPeer(verify)
          ca match {
            case None => logger.info("using system CA store for cert verification")
            case Some(certFile) =>
              logger.info("using ONLY custom CA cert for hostname verification")
              val stream = new FileInputStream(certFile)
              val cert   = CertificateFactory.getInstance("X.509").generateCertificate(stream)
              cert match {
                case x509: X509Certificate =>
                  val ldap = new LdapName(x509.getSubjectX500Principal.getName)
                  ldap.getRdns.asScala.find(_.getType == "CN").map(_.getValue.toString) match {
                    case Some(cn) if cn != host =>
                      logger.error(s"custom certificate CN=$cn, but redis hostname is '$host'")
                      logger.error("If you have verify=full option set, it's going to break due to hostname mismatch")
                      logger.error(
                        "Either set verify=ca to ignore the issue, or use a valid CA cert used to sign the redis TLS cert"
                      )
                    case Some(cn) =>
                      logger.info(s"using custom cert for CN=$cn, redis host=$host")
                    case None =>
                      logger.info("CN is not defined in cert")
                  }
                case _ =>
                  logger.warn("Not expected to see the non-X509 certificate here, hopefully you know what you're doing")
              }
              stream.close()
              val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
              keystore.load(null, null)
              keystore.setCertificateEntry("redis", cert)
              val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
              tmf.init(keystore)
              val ssl = SslOptions
                .builder()
                .jdkSslProvider()
                .trustManager(tmf)
              clientOptions.sslOptions(ssl.build())
          }
        case _ => logger.info("TLS disabled")
      }
      val client = io.lettuce.core.RedisClient.create(uri.build())
      client.setOptions(clientOptions.build())
      client
    }
}
