package ai.metarank.main

import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import cats.effect.IO
import com.fasterxml.sort.std.{RawTextLineReader, RawTextLineWriter}
import com.fasterxml.sort.{DataReader, DataReaderFactory, DataWriter, DataWriterFactory, SortConfig, Sorter}
import fs2.io.file.{Files, Path}
import fs2.Stream
import io.circe.syntax._
import io.circe.parser._

import java.io.{BufferedInputStream, BufferedOutputStream, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.util.Comparator

object Sort extends Logging {
  object Reader extends DataReaderFactory[Event] {
    override def constructReader(in: InputStream): DataReader[Event] = new DataReader[Event] {
      val nested   = new RawTextLineReader(in)
      var lastSize = 1024
      override def readNext(): Event = {
        val next = nested.readNext()
        Option(next) match {
          case None =>
            logger.info("EOF reached")
            null
          case Some(lineBytes) =>
            lastSize = next.length
            val line = new String(lineBytes)
            decode[Event](line) match {
              case Right(event) => event
              case Left(err) =>
                logger.error(s"cannot parse $line", err)
                null
            }

        }

      }
      override def estimateSizeInBytes(item: Event): Int = lastSize
      override def close(): Unit                         = nested.close()
    }
  }

  object Writer extends DataWriterFactory[Event] {
    override def constructWriter(out: OutputStream): DataWriter[Event] = new DataWriter[Event] {
      val nested                                 = new RawTextLineWriter(out)
      override def writeEntry(item: Event): Unit = nested.writeEntry((item.asJson.noSpaces).getBytes())
      override def close(): Unit                 = nested.close()
    }
  }
  object EventComparator extends Comparator[Event] {
    override def compare(t: Event, t1: Event): Int = t.timestamp.ts.compare(t1.timestamp.ts)
  }
  def run(args: SortArgs): IO[Unit] = IO {
    logger.info(s"Sorting ${args.in}")
    val sorter = new Sorter[Event](new SortConfig(), Reader, Writer, EventComparator)
    val source = new BufferedInputStream(new FileInputStream(args.in.toFile), 1024 * 1024)
    val dest   = new BufferedOutputStream(new FileOutputStream(args.out.toFile), 1024 * 1024)
    sorter.sort(source, dest)
    source.close()
    dest.close()
  }
}
