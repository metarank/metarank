package ai.metarank.main

import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.main.CliArgs.SortArgs
import ai.metarank.model.{Event, Timestamp}
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import cats.effect.IO
import com.fasterxml.sort.std.{RawTextLineReader, RawTextLineWriter}
import com.fasterxml.sort.{DataReader, DataReaderFactory, DataWriter, DataWriterFactory, SortConfig, Sorter}
import io.circe.syntax._
import io.circe.parser._
import org.apache.commons.io.FileUtils

import java.io.{BufferedInputStream, BufferedOutputStream, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.nio.file.{Files, Path}
import java.util.Comparator
import scala.jdk.CollectionConverters._

object Sort extends Logging {
  case class SortableEvent(ts: Timestamp, json: String) extends Comparable[SortableEvent] {
    override def compareTo(t: SortableEvent): Int = java.lang.Long.compare(ts.ts, t.ts.ts)
  }

  object EventReaderFactory extends DataReaderFactory[SortableEvent] {
    override def constructReader(in: InputStream): DataReader[SortableEvent] = new EventReader(in)
  }

  class EventReader(in: InputStream) extends DataReader[SortableEvent] {
    val nested   = new RawTextLineReader(in)
    var lastSize = 0
    var count    = 0L

    override def readNext(): SortableEvent = {
      val next = nested.readNext()
      Option(next) match {
        case None =>
          logger.info(s"EOF reached, read $count unsorted events")
          null
        case Some(lineBytes) =>
          lastSize += next.length
          count += 1
          if (count % 12345 == 0) {
            logger.info(s"read $count unsorted events")
          }
          val line = new String(lineBytes)
          decode[Event](line) match {
            case Right(event) => SortableEvent(event.timestamp, line)
            case Left(err) =>
              logger.error(s"cannot parse $line", err)
              null
          }
      }
    }

    override def estimateSizeInBytes(item: SortableEvent): Int = math.round(lastSize.toFloat / count.toFloat)
    override def close(): Unit                                 = nested.close()
  }
  object EventWriterFactory extends DataWriterFactory[SortableEvent] {
    override def constructWriter(out: OutputStream): DataWriter[SortableEvent] = new EventWriter(out)
  }
  class EventWriter(out: OutputStream) extends DataWriter[SortableEvent] {
    val nested                                         = new RawTextLineWriter(out)
    override def writeEntry(item: SortableEvent): Unit = nested.writeEntry(item.json.getBytes())
    override def close(): Unit                         = nested.close()
  }

  object EventTimestampComparator extends Comparator[SortableEvent] {
    override def compare(t: SortableEvent, t1: SortableEvent): Int = t.compareTo(t1)
  }

  def run(args: SortArgs): IO[Unit] = {
    if (args.in.toFile.isDirectory) {
      logger.info(s"Sorting all files in directory ${args.in}")
      val single = Files.createTempFile("metarank_presort_", ".json")
      single.toFile.deleteOnExit()
      val stream = new BufferedOutputStream(new FileOutputStream(single.toFile))
      for {
        start <- IO(System.currentTimeMillis())
        _ <- FileEventSource(FileInputConfig(args.in.toString)).stream
          .foreach(e =>
            IO {
              stream.write(e.asJson.noSpaces.getBytes())
              stream.write('\n'.toInt)
            }
          )
          .compile
          .drain
        _ <- IO(stream.close())
        _ <- info(s"merged all input files into a single file: ${single.toString}")
        _ <- sortFile(single, args.out)
      } yield {
        logger.info(s"Sorting done in ${System.currentTimeMillis() - start}ms")
      }

    } else {
      for {
        start <- IO(System.currentTimeMillis())
        _     <- info(s"Sorting single file ${args.in}")
        _     <- sortFile(args.in, args.out)
      } yield {
        logger.info(s"Sorting done in ${System.currentTimeMillis() - start}ms")
      }

    }
  }
  val MAX_MEM = 128 * 1024 * 1024

  def sortFile(in: Path, out: Path): IO[Unit] = IO {
    val conf = new SortConfig().withMaxMemoryUsage(MAX_MEM)
    logger.info(s"using ${FileUtils.byteCountToDisplaySize(MAX_MEM)} RAM for on-disk merge sort")
    val sorter = new Sorter[SortableEvent](conf, EventReaderFactory, EventWriterFactory, EventTimestampComparator)
    val source = new BufferedInputStream(new FileInputStream(in.toFile), 1024 * 1024)
    val dest   = new BufferedOutputStream(new FileOutputStream(out.toFile), 1024 * 1024)
    val sortedIterator = sorter.sort(new EventReader(source))
    logger.info(s"on-disk merge pre-sorting done, ${sorter.getNumberOfPreSortFiles} shards")
    var count     = 0
    var byteCount = 0L
    sortedIterator.asScala.foreach(e => {
      count += 1
      val bytes = (e.json + "\n").getBytes()
      byteCount += bytes.length
      if (count % 12345 == 0) logger.info(s"wrote $count sorted events, ${FileUtils.byteCountToDisplaySize(byteCount)}")
      dest.write(bytes)
    })

    source.close()
    dest.close()
    logger.info(s"written sorted file: ${out.toString}, ${FileUtils.byteCountToDisplaySize(byteCount)}, $count events")
  }
}
