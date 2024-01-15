package ai.metarank.ml.rank

import ai.metarank.config.BoosterConfig.{LightGBMConfig, XGBoostConfig}
import ai.metarank.config.Selector.AcceptSelector
import ai.metarank.config.{BoosterConfig, ModelConfig, Selector}
import ai.metarank.flow.{ClickthroughQuery, PrintProgress}
import ai.metarank.flow.PrintProgress.ProgressPeriod
import ai.metarank.main.command.Train.info
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.{Split, splitDecoder}
import ai.metarank.ml.Model.{ItemScore, RankModel, Response}
import ai.metarank.ml.Predictor.{EmptyDatasetException, RankPredictor}
import ai.metarank.ml.{Model, Predictor}
import ai.metarank.model.FeatureWeight.{SingularWeight, VectorWeight}
import ai.metarank.model.{FeatureWeight, QueryMetadata, TrainValues}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.{IO, ParallelF}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import io.github.metarank.ltrlib.booster.{Booster, LightGBMBooster, LightGBMOptions, XGBoostBooster, XGBoostOptions}
import io.github.metarank.ltrlib.metric.{MRR, Metric, NDCG}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor, Feature}
import io.github.metarank.ltrlib.ranking.pairwise.LambdaMART
import org.apache.commons.io.FileUtils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import scala.util.{Failure, Random, Success, Try}

object LambdaMARTRanker {

  import ai.metarank.util.DurationJson._

  case class LambdaMARTConfig(
      backend: BoosterConfig,
      features: NonEmptyList[FeatureName],
      weights: Map[String, Double],
      selector: Selector = AcceptSelector(),
      split: SplitStrategy = SplitStrategy.default
  ) extends ModelConfig

  val BITSTREAM_VERSION = 2

  case class LambdaMARTPredictor(name: String, config: LambdaMARTConfig, desc: DatasetDescriptor)
      extends RankPredictor[LambdaMARTConfig, LambdaMARTModel]
      with Logging {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[LambdaMARTModel] = for {
      clickthroughs <- loadDataset(data)
      split         <- splitDataset(config.split, desc, clickthroughs)
      _             <- checkDatasetSize(split.train)
      result        <- IO(makeBooster(split))
      model         <- IO.pure(LambdaMARTModel(name, config, result))
      ndcg20        <- IO(model.eval(split.test, NDCG(20, nolabels = 1.0, relpow = true)))
      _             <- info(s"NDCG20: source=${ndcg20.noopValue} reranked=${ndcg20.value} random=${ndcg20.randomValue}")
      ndcg10        <- IO(model.eval(split.test, NDCG(10, nolabels = 1.0, relpow = true)))
      _             <- info(s"NDCG10: source=${ndcg10.noopValue} reranked=${ndcg10.value} random=${ndcg10.randomValue}")
      ndcg5         <- IO(model.eval(split.test, NDCG(5, nolabels = 1.0, relpow = true)))
      _             <- info(s"NDCG5: source=${ndcg5.noopValue} reranked=${ndcg5.value} random=${ndcg5.randomValue}")
      mrr           <- IO(model.eval(split.test, MRR))
      _             <- info(s"MRR: source=${mrr.noopValue} reranked=${mrr.value} random=${mrr.randomValue}")
    } yield {
      model
    }

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

    override def load(bytes: Option[Array[Byte]]): IO[LambdaMARTModel] = IO.fromEither(loadSync(bytes))

    def loadSync(bytes: Option[Array[Byte]]): Either[Throwable, LambdaMARTModel] = bytes match {
      case None => Left(new Exception(s"cannot load model: not found, maybe you forgot to run train?"))
      case Some(blob) =>
        val stream = new DataInputStream(new ByteArrayInputStream(blob))
        Try(stream.readByte()) match {
          case Success(BITSTREAM_VERSION) =>
            val featuresSize = stream.readInt()
            val features     = (0 until featuresSize).map(_ => FeatureName(stream.readUTF())).toList
            if (features != config.features.toList) {
              val expected = features.map(_.value)
              val actual   = config.features.map(_.value).toList
              Left(new Exception(s"""booster trained with $expected features, but config defines $actual
                                    |You may need to retrain the model with the newer config""".stripMargin))
            } else {
              val boosterType = stream.readByte()
              val size        = stream.readInt()
              val buf         = new Array[Byte](size)
              stream.read(buf)
              boosterType match {
                case 0     => Right(LambdaMARTModel(name, config, LightGBMBooster(buf)))
                case 1     => Right(LambdaMARTModel(name, config, XGBoostBooster(buf)))
                case other => Left(new Exception(s"unsupported booster tag $other"))
              }
            }
          case Success(other) =>
            Left(
              new Exception(s"unsupported bitstream version $other (expected $BITSTREAM_VERSION), please re-run train")
            )
          case Failure(ex) => Left(ex)
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

  case class LambdaMARTModel(name: String, conf: LambdaMARTConfig, booster: Booster[_]) extends RankModel with Logging {
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

    def eval(dataset: Dataset, metric: Metric): MetricValue = {
      val metricValue = booster.eval(dataset, metric)
      val y           = dataset.groups.map(_.labels).toArray
      val yrand = for {
        group <- dataset.groups
      } yield {
        val indices = new Array[Double](group.labels.length)
        var i       = 0
        while (i < indices.length) {
          indices(i) = Random.nextDouble()
          i += 1
        }
        indices
      }
      val metricRandom = metric.eval(y, yrand.toArray)
      val yhat = for {
        group <- dataset.groups
      } yield {
        val indices = new Array[Double](group.labels.length)
        var i       = 0
        while (i < indices.length) {
          indices(i) = (indices.length - i) / indices.length.toDouble
          i += 1
        }
        indices
      }
      val metricNoop = metric.eval(y, yhat.toArray)

      MetricValue(metricValue, metricNoop, metricRandom)
    }

  }
  case class MetricValue(value: Double, noopValue: Double, randomValue: Double)

  implicit val lmDecoder: Decoder[LambdaMARTConfig] = Decoder.instance(c =>
    for {
      backend  <- c.downField("backend").as[Option[BoosterConfig]]
      features <- c.downField("features").as[NonEmptyList[FeatureName]]
      weights  <- c.downField("weights").as[Option[Map[String, Double]]]
      selector <- c.downField("selector").as[Option[Selector]].map(_.getOrElse(AcceptSelector()))
      split    <- c.downField("split").as[Option[SplitStrategy]].map(_.getOrElse(SplitStrategy.default))
    } yield {
      LambdaMARTConfig(backend.getOrElse(XGBoostConfig()), features, weights.getOrElse(Map.empty), selector, split)
    }
  )
  implicit val lmEncoder: Encoder[LambdaMARTConfig] = deriveEncoder

}
