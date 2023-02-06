package ai.metarank.main.command.train

import ai.metarank.main.command.train.SplitStrategy.Split
import ai.metarank.model.QueryMetadata
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder, Json}
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor}

import scala.util.{Failure, Random, Success}

sealed trait SplitStrategy extends Logging {
  def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split]
}

object SplitStrategy {
  val MIN_SPLIT = 1
  val default   = TimeSplit(80)
  case class Split(train: Dataset, test: Dataset)

  case class RandomSplit(ratioPercent: Int) extends SplitStrategy {
    override def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split] = IO {
      logger.info(s"using random split strategy, ratio=$ratioPercent%")
      val (train, test) = Random.shuffle(queries).partition(_ => Random.nextInt(100) < ratioPercent)
      Split(Dataset(desc, train.map(_.query)), Dataset(desc, test.map(_.query)))
    }
  }

  case class TimeSplit(ratioPercent: Int) extends SplitStrategy {
    override def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split] = for {
      _ <- info(s"using time split strategy, ratio=$ratioPercent%")
      size = queries.size
      position <- queries.size match {
        case 0 | 1 => IO.raiseError(new Exception(s"dataset size ($size items) is too small to be split"))
        case 2     => warnSmallDataset(size) *> IO.pure(1)
        case gt =>
          IO.whenA(gt < MIN_SPLIT)(warnSmallDataset(size)) *> IO(math.round(queries.size * (ratioPercent / 100.0f)))
      }
    } yield {
      val (train, test) = queries.sortBy(_.ts.ts).splitAt(position)
      Split(Dataset(desc, train.map(_.query)), Dataset(desc, test.map(_.query)))
    }
    private def warnSmallDataset(size: Int) = warn(
      s" dataset size of $size items is too small - expect weird ranking results"
    )
  }

  case class HoldLastStrategy(ratioPercent: Int) extends SplitStrategy {
    override def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split] = IO {
      logger.info(s"using hold-last split strategy, ratio=$ratioPercent%")
      val sessions = queries.groupBy(_.user).values.toList.map { cts =>
        {
          val sorted = cts.sortBy(_.ts.ts)
          sorted match {
            case x1 :: Nil       => (sorted, Nil)
            case x1 :: x2 :: Nil => (List(x1), List(x2))
            case other           => sorted.splitAt(math.round(queries.size * (ratioPercent / 100.0f)))
          }
        }
      }
      val train = sessions.flatMap(_._1)
      val test  = sessions.flatMap(_._2)
      Split(Dataset(desc, train.map(_.query)), Dataset(desc, test.map(_.query)))
    }
  }

  val splitPattern = "([a-z_]+)=([0-9]{1,3})%".r
  def parse(in: String): Either[Exception, SplitStrategy] = in match {
    case "random"                         => Right(RandomSplit(80))
    case splitPattern("random", ratio)    => Right(RandomSplit(ratio.toInt))
    case "time"                           => Right(TimeSplit(80))
    case splitPattern("time", ratio)      => Right(TimeSplit(ratio.toInt))
    case "hold_last"                      => Right(HoldLastStrategy(80))
    case splitPattern("hold_last", ratio) => Right(HoldLastStrategy(ratio.toInt))
    case other                            => Left(new Exception(s"split pattern $other cannot be parsed"))
  }

  implicit val splitDecoder: Decoder[SplitStrategy] = Decoder.decodeString.emapTry(str => parse(str).toTry)
  implicit val splitEncoder: Encoder[SplitStrategy] = Encoder.instance {
    case RandomSplit(ratio)      => Json.fromString(s"random=$ratio%")
    case TimeSplit(ratio)        => Json.fromString(s"time=$ratio%")
    case HoldLastStrategy(ratio) => Json.fromString(s"hold_last=$ratio%")
  }
}
