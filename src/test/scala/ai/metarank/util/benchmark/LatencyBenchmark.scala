package ai.metarank.util.benchmark

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig.SortingType
import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.fstore.{ClickthroughStore, Persistence}
import ai.metarank.main.CliArgs.StandaloneArgs
import ai.metarank.main.command.{Serve, Standalone}
import ai.metarank.model.Event.{ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{Event, EventId, Timestamp}
import ai.metarank.source.format.JsonFormat
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp}
import org.apache.commons.io.IOUtils
import org.http4s.{Entity, Method, Request, Uri}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import cats.implicits._

import scala.concurrent.duration._
import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.UUID
import scala.util.Random
import io.circe.syntax._
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import scodec.bits.ByteVector

object LatencyBenchmark extends IOApp with Logging {
  case class Services(store: Persistence, client: Client[IO])
  case class BenchResult(items: Int, latencies: Array[Long]) {
    val dist = latencies.groupMapReduce(l => 100000 * math.floor(l.toDouble / 100000))(_ => 1)(_ + _)
  }
  case class Measurement(ts: Long, latency: Long)

  object RandomRequest {
    def apply(items: Int, userRange: Range, itemRange: Range): Event = {
      val uid = (userRange.start + Random.nextInt(userRange.end - userRange.start)).toString
      RankingEvent(
        id = EventId(UUID.randomUUID().toString),
        timestamp = Timestamp.now,
        user = Some(UserId(uid)),
        session = Some(SessionId(uid)),
        fields = Nil,
        items = NonEmptyList.fromListUnsafe(
          Random.shuffle((0 until itemRange.end).toList).take(items).map(id => ItemRelevancy(ItemId(id.toString)))
        )
      )
    }
  }

  val REQUESTS = 5000
  val ITEMS    = List(25, 50, 75, 100, 125, 150, 175, 200, 225, 250, 275, 300)

  override def run(args: List[String]): IO[ExitCode] = for {
    dataPath <- IO.fromOption(args.lift(1))(new Exception("need data"))
    confPath <- IO.fromOption(args.lift(0))(new Exception("need conf"))
    conf    <- Config.load(IOUtils.toString(new FileInputStream(new File(confPath)), StandardCharsets.UTF_8), Map.empty)
    mapping <- IO(FeatureMapping.fromFeatureSchema(conf.features, conf.models).optimize())

    results <- start(mapping, conf, confPath, dataPath).use(s =>
      for {
        warmup  <- bench(s.client, "all", 10, 1000)
        _       <- info("warmup done")
        results <- ITEMS.map(items => bench(s.client, "all", items, REQUESTS)).sequence
      } yield { results }
    )
  } yield {
    val perc   = List(50, 80, 90, 95, 99)
    val file   = s"/tmp/bench_nocache_json.csv"
    val stream = new FileOutputStream(new File(file))
    stream.write(s"items,${perc.mkString(",")}\n".getBytes())
    results.foreach(r => {
      val p = new Percentile()
      p.setData(r.latencies.map(_.toDouble))
      stream.write(s"${r.items},${perc.map(p.evaluate(_)).mkString(",")}\n".getBytes())
    })
    stream.close()
    logger.info(s"wrote $file")
    ExitCode.Success
  }

  def start(mapping: FeatureMapping, conf: Config, confPath: String, dataPath: String) = for {
    store <- Persistence.fromConfig(mapping.schema, conf.state)
    cts   <- ClickthroughStore.fromConfig(conf.train)
    buffer <- Resource.liftK(
      Standalone
        .prepare(
          conf,
          store,
          cts,
          mapping,
          StandaloneArgs(
            conf = Paths.get(confPath),
            data = Paths.get(dataPath),
            offset = SourceOffset.Earliest,
            validation = false,
            format = JsonFormat,
            sort = SortingType.SortByName
          )
        )
    )
    api    <- Serve.api(store, cts, mapping, conf.api, buffer).background
    client <- BlazeClientBuilder[IO].withConnectTimeout(1.second).withRequestTimeout(1.second).resource
  } yield {
    Services(store, client)
  }

  def bench(client: Client[IO], model: String, items: Int, requests: Int): IO[BenchResult] = for {
    endpoint <- IO.fromEither(Uri.fromString(s"http://localhost:8080/rank/$model"))
    jsons    <- IO((0 until requests).toList.map(_ => RandomRequest(items, 0 until 1000, 0 until 1000).asJson.noSpaces))
    _        <- info(s"generated $requests requests for model=$model items=$items")
    latencies <- jsons
      .map(json =>
        for {
          start <- IO(System.nanoTime())
          _ <- client.expect[String](
            Request[IO](
              method = Method.POST,
              uri = endpoint,
              entity = Entity.strict(ByteVector(json.getBytes()))
            )
          )
        } yield {
          System.nanoTime() - start
        }
      )
      .sequence
  } yield {
    BenchResult(items, latencies.toArray)
  }

}
