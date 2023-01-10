package ai.metarank.util

import cats.effect.IO
import fs2.{Pull, Stream}
object SortedGroupBy {
  def groupBy[T, K](key: T => K): fs2.Pipe[IO, T, List[T]] = {
    def next(s: Stream[IO, T], buf: List[T] = Nil): Pull[IO, List[T], Unit] = {
      s.pull.uncons1.flatMap {
        case Some((item, tail)) =>
          buf match {
            case head :: _ if (key(head) == key(item)) => next(tail, buf :+ item)
            case head :: _                             => Pull.output1(buf) >> next(tail, List(item))
            case Nil                                   => next(tail, List(item))
          }
        case None =>
          buf match {
            case Nil      => Pull.done
            case nonEmpty => Pull.output1(nonEmpty) >> Pull.done
          }
      }

    }
    in => next(in).stream
  }
}
