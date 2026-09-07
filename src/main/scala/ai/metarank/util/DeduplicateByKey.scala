package ai.metarank.util

import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

/** Drops every element whose key was already seen earlier in the stream, keeping the first occurrence. The seen-set is
  * carried as Pull state, so each run of the stream starts fresh. Memory grows with the number of distinct keys.
  */
object DeduplicateByKey {
  def apply[T, K](key: T => K): Pipe[IO, T, T] = {
    def next(s: Stream[IO, T], seen: Set[K]): Pull[IO, T, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((item, tail)) =>
          val k = key(item)
          if (seen.contains(k)) next(tail, seen)
          else Pull.output1(item) >> next(tail, seen + k)
        case None => Pull.done
      }
    }
    in => next(in, Set.empty).stream
  }
}
