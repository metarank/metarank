package ai.metarank.main.command.train

import ai.metarank.main.command.train.SplitStrategy.Split
import ai.metarank.model.QueryMetadata
import ai.metarank.util.Logging
import cats.effect.IO
import io.github.metarank.ltrlib.model.{Dataset, DatasetDescriptor}

import scala.util.Random

sealed trait SplitStrategy extends Logging {
  def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split]
}

object SplitStrategy {
  val default = TimeSplit(80)
  case class Split(train: Dataset, test: Dataset)

  case class RandomSplit(ratioPercent: Int) extends SplitStrategy {
    override def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split] = IO {
      logger.info(s"using random split strategy, ratio=$ratioPercent%")
      val (train, test) = Random.shuffle(queries).partition(_ => Random.nextInt(100) < ratioPercent)
      Split(Dataset(desc, train.map(_.query)), Dataset(desc, test.map(_.query)))
    }
  }

  case class TimeSplit(ratioPercent: Int) extends SplitStrategy {
    override def split(desc: DatasetDescriptor, queries: List[QueryMetadata]): IO[Split] = IO {
      logger.info(s"using time split strategy, ratio=$ratioPercent%")
      val (train, test) = queries.sortBy(_.ts.ts).splitAt(math.round(queries.size * (ratioPercent / 100.0f)))
      Split(Dataset(desc, train.map(_.query)), Dataset(desc, test.map(_.query)))
    }
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
    case "user"                           => Right(HoldLastStrategy(80))
    case splitPattern("hold_last", ratio) => Right(HoldLastStrategy(ratio.toInt))
    case other                            => Left(new Exception(s"split pattern $other cannot be parsed"))
  }
}
