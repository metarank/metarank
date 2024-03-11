package ai.metarank.ml.rank

import ai.metarank.config.BoosterConfig.{LightGBMConfig, XGBoostConfig}
import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{BoosterConfig, ModelConfig, Selector, WarmupConfig}
import ai.metarank.flow.{ClickthroughQuery, PrintProgress}
import ai.metarank.flow.PrintProgress.ProgressPeriod
import ai.metarank.main.command.Train.info
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.{Split, splitDecoder}
import ai.metarank.ml.Model.{ItemScore, RankModel, Response}
import ai.metarank.ml.Predictor.{EmptyDatasetException, RankPredictor}
import ai.metarank.ml.rank.LambdaMARTRanker.EvalMetricName.{MapMetric, MrrMetric, NdcgMetric}
import ai.metarank.ml.{Model, Predictor}
import ai.metarank.model.Event.{RankItem, RankingEvent}
import ai.metarank.model.FeatureWeight.{SingularWeight, VectorWeight}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{FeatureWeight, Field, QueryMetadata, TrainValues}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.{Logging, RankingEventFormat}
import cats.data.NonEmptyList
import cats.effect.std.Queue
import cats.effect.{IO, ParallelF, Ref}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import io.github.metarank.ltrlib.booster.{Booster, LightGBMBooster, LightGBMOptions, XGBoostBooster, XGBoostOptions}
import io.github.metarank.ltrlib.metric.{MAP, MRR, Metric, NDCG}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature}
import io.github.metarank.ltrlib.ranking.pairwise.LambdaMART
import org.apache.commons.io.FileUtils
import cats.implicits._
import fs2.Pipe

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.util
import scala.util.{Failure, Random, Success, Try}

object LambdaMARTRanker extends Logging {

  import ai.metarank.util.DurationJson._

  case class LambdaMARTConfig(
      backend: BoosterConfig,
      features: NonEmptyList[FeatureName],
      weights: Map[String, Double],
      selector: Selector = AcceptSelector(),
      split: SplitStrategy = SplitStrategy.default,
      eval: List[EvalMetricName] = EvalMetricName.default,
      warmup: Option[WarmupConfig] = None
  ) extends ModelConfig

  sealed trait EvalMetricName {
    def name: String
    def cutoff: Option[Int]

    override def toString: String = cutoff match {
      case Some(value) => s"$name@$value"
      case None        => name
    }
  }
  object EvalMetricName {
    case class NdcgMetric(cutoff: Option[Int]) extends EvalMetricName {
      def name = "NDCG"
    }
    case class MapMetric(cutoff: Option[Int]) extends EvalMetricName {
      def name = "MAP"
    }
    case class MrrMetric() extends EvalMetricName {
      def cutoff = None
      def name   = "MRR"
    }

    val metricPattern       = "([A-Za-z]+)".r
    val metricCutoffPattern = "([a-zA-Z]+)@([0-9]+)".r

    val default = List(NdcgMetric(Some(10)))

    def fromString(name: String, cutoff: Option[Int] = None): Either[Throwable, EvalMetricName] =
      name.toLowerCase() match {
        case "ndcg" => Right(NdcgMetric(cutoff))
        case "map"  => Right(MapMetric(cutoff))
        case "mrr"  => Right(MrrMetric())
        case other  => Left(new Exception(s"cannot decode metric $other"))
      }

    implicit val evalMetricNameDecoder: Decoder[EvalMetricName] = Decoder.decodeString.emapTry {
      case metricCutoffPattern(name, cutoff) => fromString(name, Some(cutoff.toInt)).toTry
      case metricPattern(name)               => fromString(name).toTry
    }

    implicit val evalMetricNameEncoder: Encoder[EvalMetricName] = Encoder.encodeString.contramap {
      case NdcgMetric(Some(cutoff)) => s"ndcg@$cutoff"
      case NdcgMetric(None)         => "ndcg"
      case MapMetric(Some(cutoff))  => s"map@$cutoff"
      case MapMetric(None)          => "map"
      case MrrMetric()              => "mrr"
    }
  }

  val BITSTREAM_VERSION = 3

  case class LambdaMARTPredictor(name: String, config: LambdaMARTConfig, desc: DatasetDescriptor)
      extends RankPredictor[LambdaMARTConfig, LambdaMARTModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[LambdaMARTModel] = for {
      sampleSize           <- IO(config.warmup.map(_.sampledRequests).getOrElse(0))
      warmupRequestsBuffer <- Queue.unbounded[IO, RankingEvent]
      clickthroughs        <- loadDataset(data.through(sampleN(sampleSize, warmupRequestsBuffer)))
      split                <- splitDataset(config.split, desc, clickthroughs)
      _                    <- checkDatasetSize(split.train)
      booster              <- IO(makeBooster(split))
      warmupRequests       <- warmupRequestsBuffer.tryTakeN(None)
      model                <- IO.pure(LambdaMARTModel(name, config, booster, warmupRequests))
      _ <- config.eval.map {
        case metric @ NdcgMetric(cutoff) =>
          model
            .eval(split.test, NDCG(cutoff.getOrElse(Int.MaxValue), nolabels = 1.0, relpow = true))
            .flatMap(logMetric(metric, _))
        case metric @ MapMetric(cutoff) =>
          model.eval(split.test, MAP(cutoff.getOrElse(Int.MaxValue))).flatMap(logMetric(metric, _))
        case metric @ MrrMetric() => model.eval(split.test, MRR).flatMap(logMetric(metric, _))
      }.sequence

    } yield {
      model
    }

    private def logMetric(metric: EvalMetricName, value: MetricValue) =
      info(
        s"$metric: source=${value.noopValue} reranked=${value.value} random=${value.randomValue} (took ${value.took})"
      )

    private def sampleN(n: Int, dest: Queue[IO, RankingEvent]): Pipe[IO, TrainValues, TrainValues] = in =>
      in.evalMap(event =>
        for {
          size <- dest.size
          _ <- IO.whenA(size < n)(event match {
            case ClickthroughValues(ct, _) =>
              NonEmptyList.fromList(ct.items) match {
                case None => IO.unit
                case Some(itemsNel) =>
                  dest.offer(
                    RankingEvent(
                      id = event.id,
                      timestamp = ct.ts,
                      user = ct.user,
                      session = ct.session,
                      fields = ct.rankingFields,
                      items = itemsNel.map(item => RankItem(ItemId(item.value)))
                    )
                  )
              }
            case _ => IO.unit
          })
        } yield {
          event
        }
      )

    def makeBooster(split: Split) = {
      config.backend match {
        case LightGBMConfig(it, lr, ndcg, depth, seed, leaves, sampling, debias) =>
          val opts = LightGBMOptions(
            trees = it,
            numLeaves = leaves,
            randomSeed = seed,
            learningRate = lr,
            ndcgCutoff = ndcg,
            maxDepth = depth,
            featureFraction = sampling,
            earlyStopping = Some(20),
            debias = debias
          )
          LambdaMART(split.train, LightGBMBooster, Some(split.test), opts).fit(opts)
        case XGBoostConfig(it, lr, ndcg, depth, seed, sampling, debias) =>
          val opts = XGBoostOptions(
            trees = it,
            randomSeed = seed,
            learningRate = lr,
            ndcgCutoff = ndcg,
            maxDepth = depth,
            subsample = sampling,
            earlyStopping = Some(20),
            treeMethod = "exact", // hist/approx do not work with categories
            debias = debias
          )
          LambdaMART(split.train, XGBoostBooster, Some(split.test), opts).fit(opts)
      }
    }

    override def load(bytes: Option[Array[Byte]]): IO[LambdaMARTModel] = bytes match {
      case None => IO.raiseError(new Exception(s"cannot load model: not found, maybe you forgot to run train?"))
      case Some(blob) =>
        for {
          stream    <- IO(new DataInputStream(new ByteArrayInputStream(blob)))
          bitstream <- IO(stream.readByte())
          model     <- loadVersion(stream, bitstream)
        } yield {
          model
        }
    }

    private def loadVersion(stream: DataInputStream, bitstream: Int): IO[LambdaMARTModel] = {
      for {
        featuresSize <- IO(stream.readInt())
        features     <- IO((0 until featuresSize).map(_ => FeatureName(stream.readUTF())).toList)
        _ <- IO.whenA(features != config.features.toList)(IO.raiseError {
          val expected = features.map(_.value)
          val actual   = config.features.map(_.value).toList
          new Exception(s"""booster trained with $expected features, but config defines $actual
                           |You may need to retrain the model with the newer config""".stripMargin)
        })
        boosterType <- IO(stream.readByte())
        size        <- IO(stream.readInt())
        buf         <- IO(new Array[Byte](size))
        _           <- IO(stream.read(buf))
        warmup <- bitstream match {
          case 2 => IO(Nil)
          case 3 =>
            for {
              warmupSampleSize <- IO(stream.readInt())
              warmupRequests   <- IO((0 until warmupSampleSize).map(_ => RankingEventFormat.read(stream)))
            } yield {
              warmupRequests
            }
        }
        booster <- boosterType match {
          case 0     => IO(LightGBMBooster(buf))
          case 1     => IO(XGBoostBooster(buf))
          case other => IO.raiseError(new Exception(s"unsupported booster tag $other"))
        }
      } yield {
        LambdaMARTModel(name, config, booster, warmup.toList)
      }
    }

    def splitDataset(splitter: SplitStrategy, desc: DatasetDescriptor, clickthroughs: List[QueryMetadata]): IO[Split] =
      for {
        split <- splitter.split(desc, clickthroughs)
        _ <- split match {
          case Split(train, _) if train.groups.isEmpty =>
            IO.raiseError(
              new Exception(s"Train dataset is empty (with ${clickthroughs.size} total click-through events)")
            )
          case Split(_, test) if test.groups.isEmpty =>
            IO.raiseError(
              new Exception(s"Test dataset is empty (with ${clickthroughs.size} total click-through events)")
            )
          case Split(train, test)
              if (train.groups.size < SplitStrategy.MIN_SPLIT) || (test.groups.size < SplitStrategy.MIN_SPLIT) =>
            IO.raiseError(
              new Exception(s"""Train/test datasets are too small: train=${train.groups.size}, test=${test.groups.size}.
                               |It is not possible to train the ML model on such a small dataset.
                               |Minimal hard-coded threshold is ${SplitStrategy.MIN_SPLIT}""".stripMargin)
            )
          case Split(train, test) =>
            info(s"Train/Test split finished: ${train.groups.size}/${test.groups.size} click-through events")
        }

      } yield {
        split
      }

    def loadDataset(data: fs2.Stream[IO, TrainValues]) = for {
      clickthroughs <- data
        .collect { case c: ClickthroughValues => c }
        .filter(ctv => config.selector.accept(ctv.ct))
        .filter(_.ct.interactions.nonEmpty)
        .filter(_.values.nonEmpty)
        .map(ct =>
          QueryMetadata(
            query = ClickthroughQuery(ct.values, ct.ct.interactions, ct, config.weights, desc),
            ts = ct.ct.ts,
            user = ct.ct.user,
            fields = ct.ct.rankingFields
          )
        )
        .compile
        .toList

      _ <- info(s"loaded ${clickthroughs.size} clickthroughs")
      _ <- IO.whenA(clickthroughs.isEmpty)(
        IO.raiseError(
          EmptyDatasetException(
            """
              |Cannot train model on an empty dataset. Maybe you forgot to do 'metarank import'?
              |Possible options:
              |1. You have only interaction or only ranking events. Re-ranking model needs a complete click-throught information. Try running `metarank validate` over your config file and dataset?
              |2. Dataset inconsistency. To check for consistency issues, run 'metarank validate --config conf.yml --data data.jsonl.gz',
              |3. You've used an in-memory persistence for import, and after restart the data was lost. Maybe try setting 'state.type=redis'?
              |""".stripMargin
          )
        )
      )
    } yield {
      clickthroughs
    }
  }

  def checkDatasetSize(ds: Dataset): IO[Unit] =
    checkDatasetSize(ds.itemCount, ds.desc.dim, ds.groups.size, ds.desc.features)

  def checkDatasetSize(itemCount: Int, dim: Int, groupsCount: Int, features: List[Feature]): IO[Unit] = {
    val size = itemCount.toLong * dim.toLong
    if (size >= Int.MaxValue) {
      val featureSizes = features.map {
        case Feature.SingularFeature(name)     => name -> itemCount.toLong * 4L
        case Feature.VectorFeature(name, size) => name -> itemCount.toLong * size * 4L
        case Feature.CategoryFeature(name)     => name -> itemCount.toLong * 4L
      }
      val largestFeatures = featureSizes
        .sortBy(-_._2)
        .map { case (name, bytes) =>
          s"- $name: ${FileUtils.byteCountToDisplaySize(bytes)} RAM used"
        }
        .mkString("\n")
      IO.raiseError(
        new Exception(s"""Training dataset is too big! There are $groupsCount queries with total ${itemCount} rows.
                         |Considering that feature dimension is $dim, it requires more space than a single
                         |float[${Int.MaxValue}] array can fit in JVM. Feature dominators:
                         |$largestFeatures
                         |
                         |Possible solutions:
                         |* Reduce the dimensionality of the dataset: remove a couple of large features from the list
                         |  above.
                         |* Add sampling: only use N% of all the click-through events for training. Check docs for
                         |  models.<name>.accept option.
                         |  
                         |""".stripMargin)
      )
    } else {
      IO(
        info(s"${itemCount}x$dim dataset uses ~${FileUtils.byteCountToDisplaySize(itemCount * dim * 4L)} of JVM heap")
      )
    }
  }

  case class LambdaMARTModel(
      name: String,
      conf: LambdaMARTConfig,
      booster: Booster[_],
      warmupRequests: List[RankingEvent] = Nil
  ) extends RankModel
      with Logging
      with AutoCloseable {
    override def predict(request: QueryRequest): IO[Model.Response] = {
      IO(booster.predictMat(request.query.values, request.query.rows, request.query.columns).toList).flatMap {
        case head :: tail =>
          for {
            items <- IO(request.items.zip(NonEmptyList.of(head, tail: _*)).map { case (item, score) =>
              ItemScore(item.id, score)
            })
          } yield {
            Response(items)
          }
        case _ => IO.raiseError(new Exception("booster.predictMat returned empty array"))
      }
    }

    override def close(): Unit = {
      booster.close()
    }

    override def isClosed(): Boolean = booster.isClosed()

    override def save(): Option[Array[Byte]] = {
      val buf    = new ByteArrayOutputStream()
      val stream = new DataOutputStream(buf)
      stream.writeByte(BITSTREAM_VERSION)
      stream.writeInt(conf.features.size)
      conf.features.toList.foreach(f => stream.writeUTF(f.value))
      val bytes = booster.save()
      booster match {
        case _: LightGBMBooster =>
          stream.writeByte(0)
          stream.writeInt(bytes.length)
          stream.write(bytes)
        case _: XGBoostBooster =>
          stream.writeByte(1)
          stream.writeInt(bytes.length)
          stream.write(bytes)
        case _ =>
          logger.warn("serializing unsupported booster")
      }
      stream.writeInt(warmupRequests.size)
      warmupRequests.foreach(request => RankingEventFormat.write(request, stream))
      Some(buf.toByteArray)
    }

    def weights(desc: DatasetDescriptor): Map[String, FeatureWeight] = {
      val w = booster.weights()
      val result = for {
        feature <- desc.features
        offset  <- desc.offsets.get(feature)
      } yield {
        feature match {
          case Feature.SingularFeature(_)     => feature.name -> SingularWeight(w(offset))
          case Feature.VectorFeature(_, size) => feature.name -> VectorWeight(w.slice(offset, offset + size))
          case Feature.CategoryFeature(_)     => feature.name -> SingularWeight(w(offset))
        }
      }
      result.toMap
    }

    def eval(dataset: Dataset, metric: Metric): IO[MetricValue] = {
      val start           = System.currentTimeMillis()
      val predictedScores = dataset.groups.map(g => booster.predictMat(g.values, g.rows, g.columns))
      val randomScores    = dataset.groups.map(g => randomArray(g.labels.length))
      val noopScores      = dataset.groups.map(g => noopArray(g.labels.length))
      val labels          = dataset.groups.map(_.labels)

      for {
        predictValue <- evalOne(metric, predictedScores, labels)
        noopValue    <- evalOne(metric, noopScores, labels)
        randomValue  <- evalOne(metric, randomScores, labels)
      } yield {
        MetricValue(predictValue, noopValue, randomValue, took = System.currentTimeMillis() - start)
      }
    }
    def randomArray(len: Int): Array[Double] = {
      val result = new Array[Double](len)
      var i      = 0
      while (i < len) {
        result(i) = Random.nextDouble()
        i += 1
      }
      result
    }

    def noopArray(len: Int): Array[Double] = {
      val result = new Array[Double](len)
      var i      = 0
      val dlen   = len.toDouble
      while (i < len) {
        result(i) = (len - i) / dlen
        i += 1
      }
      result
    }

    def evalOne(metric: Metric, scores: List[Array[Double]], labels: List[Array[Double]]): IO[Double] = IO(
      metric.eval(labels.toArray, scores.toArray)
    )

  }
  case class MetricValue(value: Double, noopValue: Double, randomValue: Double, took: Long)

  val forbiddenFeatureNames = Set("models", "state", "values")
  implicit val lmDecoder: Decoder[LambdaMARTConfig] = Decoder
    .instance(c =>
      for {
        backendOption <- c.downField("backend").as[Option[BoosterConfig]]
        features      <- c.downField("features").as[NonEmptyList[FeatureName]]
        weightsOption <- c.downField("weights").as[Option[Map[String, Double]]]
        selector      <- c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector()))
        split         <- c.downField("split").as[Option[SplitStrategy]].map(_.getOrElse(SplitStrategy.default))
        eval          <- c.downField("eval").as[Option[List[EvalMetricName]]].map(_.getOrElse(EvalMetricName.default))
        warmup        <- c.downField("warmup").as[Option[WarmupConfig]]
      } yield {
        val backend        = backendOption.getOrElse(XGBoostConfig())
        val weights        = weightsOption.getOrElse(Map.empty)
        val clippedWeights = maybeClipWeights(backend, weights)
        LambdaMARTConfig(backend, features, clippedWeights, selector, split, eval, warmup)
      }
    )
    .ensure(
      conf => conf.features.forall(f => !forbiddenFeatureNames.contains(f.value)),
      s"feature names ${forbiddenFeatureNames} are reserved names, you cannot use them"
    )

  implicit val lmEncoder: Encoder[LambdaMARTConfig] = deriveEncoder

  def maybeClipWeights(backend: BoosterConfig, weights: Map[String, Double]): Map[String, Double] = {
    backend match {
      case x: XGBoostConfig if weights.values.exists(_ > 31) =>
        logger.warn(
          "XGBoost uses exponential weighting during optimization, and does not allow" +
            s" item weights being > 31. Current weights: ${weights}. Clipping all weights to 31."
        )
        weights.map { case (name, w) =>
          name -> math.min(31.0, w)
        }
      case _ =>
        // no change
        weights
    }
  }
}
