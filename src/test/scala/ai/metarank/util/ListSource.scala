package ai.metarank.util

import ai.metarank.util.ListSource.{
  ListEnumerator,
  ListReader,
  ListSplit,
  ListSplitCheckpointSerializer,
  ListSplitSerializer
}
import com.google.common.io.ByteArrayDataOutput
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.connector.source.{
  Boundedness,
  ReaderOutput,
  Source,
  SourceReader,
  SourceReaderContext,
  SourceSplit,
  SplitEnumerator,
  SplitEnumeratorContext,
  SplitsAssignment
}

import scala.jdk.CollectionConverters._
import org.apache.flink.core.io.{InputStatus, SimpleVersionedSerializer}
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

case class ListSource[T](values: List[T])(implicit val ti: TypeInformation[T])
    extends Source[T, ListSplit[T], List[ListSplit[T]]] {

  override def createEnumerator(
      enumContext: SplitEnumeratorContext[ListSplit[T]]
  ): SplitEnumerator[ListSplit[T], List[ListSplit[T]]] = ListEnumerator[T](values, enumContext)

  override def createReader(readerContext: SourceReaderContext): SourceReader[T, ListSplit[T]] =
    new ListReader[T](readerContext)

  override def getBoundedness: Boundedness = Boundedness.BOUNDED

  override def getEnumeratorCheckpointSerializer: SimpleVersionedSerializer[List[ListSplit[T]]] =
    ListSplitCheckpointSerializer(ti.createSerializer(new ExecutionConfig()))

  override def getSplitSerializer: SimpleVersionedSerializer[ListSplit[T]] = ListSplitSerializer(
    ti.createSerializer(new ExecutionConfig())
  )

  override def restoreEnumerator(
      enumContext: SplitEnumeratorContext[ListSplit[T]],
      checkpoint: List[ListSplit[T]]
  ): SplitEnumerator[ListSplit[T], List[ListSplit[T]]] = new ListEnumerator(mutable.Queue(checkpoint: _*), enumContext)
}

object ListSource {
  case class ListSplit[T](values: List[T]) extends SourceSplit {
    override def splitId(): String = (values.headOption, values.lastOption) match {
      case (Some(head), Some(tail)) => s"ListSplit($head - $tail)"
      case _                        => "ListSplit(empty)"
    }
  }

  case class ListSplitSerializer[T](ti: TypeSerializer[T]) extends SimpleVersionedSerializer[ListSplit[T]] {
    override def deserialize(version: Int, serialized: Array[Byte]): ListSplit[T] = {
      val stream = new ByteArrayInputStream(serialized)
      val view   = new DataInputViewStreamWrapper(stream)
      val size   = view.readInt()
      val buffer = for {
        _ <- 0 until size
      } yield {
        ti.deserialize(view)
      }
      ListSplit(buffer.toList)
    }

    override def serialize(obj: ListSplit[T]): Array[Byte] = {
      val stream = new ByteArrayOutputStream()
      val view   = new DataOutputViewStreamWrapper(stream)
      view.writeInt(obj.values.size)
      obj.values.foreach(split => ti.serialize(split, view))
      stream.toByteArray
    }
    override def getVersion: Int = 1
  }

  case class ListSplitCheckpointSerializer[T](ti: TypeSerializer[T])
      extends SimpleVersionedSerializer[List[ListSplit[T]]] {
    override def serialize(obj: List[ListSplit[T]]): Array[Byte] = {
      val stream = new ByteArrayOutputStream()
      val view   = new DataOutputViewStreamWrapper(stream)
      view.writeInt(obj.size)
      for {
        split <- obj
      } {
        view.writeInt(split.values.size)
        split.values.foreach(item => ti.serialize(item, view))
      }
      stream.toByteArray
    }

    override def deserialize(version: Int, serialized: Array[Byte]): List[ListSplit[T]] = {
      val stream = new ByteArrayInputStream(serialized)
      val view   = new DataInputViewStreamWrapper(stream)
      val size   = view.readInt()
      val splits = for {
        _ <- 0 until size
      } yield {
        val splitSize = view.readInt()
        val items = for {
          _ <- 0 until splitSize
        } yield ti.deserialize(view)
        ListSplit(items.toList)
      }
      splits.toList
    }

    override def getVersion: Int = 1
  }

  class ListEnumerator[T](queue: mutable.Queue[ListSplit[T]], ctx: SplitEnumeratorContext[ListSplit[T]])
      extends SplitEnumerator[ListSplit[T], List[ListSplit[T]]] {

    override def start(): Unit = {}

    override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = {
      Try(queue.dequeue()) match {
        case Failure(_)     => ctx.signalNoMoreSplits(subtaskId)
        case Success(split) => ctx.assignSplit(split, subtaskId)
      }
    }

    override def addSplitsBack(splits: util.List[ListSplit[T]], subtaskId: Int): Unit =
      queue.enqueueAll(splits.asScala)

    override def snapshotState(checkpointId: Long): List[ListSplit[T]] = queue.toList

    override def close(): Unit = {}

    override def addReader(subtaskId: Int): Unit = {}
  }

  object ListEnumerator {
    def apply[T](values: List[T], ctx: SplitEnumeratorContext[ListSplit[T]]) = {
      val splitSize = math.ceil(values.size.toDouble / ctx.currentParallelism()).toInt
      val br        = 1
      new ListEnumerator[T](
        queue = mutable.Queue(values.grouped(splitSize).toList.map(list => ListSplit(list)): _*),
        ctx = ctx
      )
    }

  }

  class ListReader[T](ctx: SourceReaderContext) extends SourceReader[T, ListSplit[T]] {
    var assigned   = false
    val splitQueue = mutable.Queue[ListSplit[T]]()

    override def pollNext(output: ReaderOutput[T]): InputStatus = {
      val b     = 1
      val items = splitQueue.toList.flatMap(x => x.values)
      items match {
        case Nil if assigned  => InputStatus.END_OF_INPUT
        case Nil if !assigned => InputStatus.NOTHING_AVAILABLE
        case values =>
          values.foreach(output.collect)
          InputStatus.END_OF_INPUT
      }
    }

    override def start(): Unit = {
      ctx.sendSplitRequest()
    }

    override def addSplits(splits: util.List[ListSplit[T]]): Unit = {
      val b = 1
      assigned = true
      splitQueue.enqueueAll(splits.asScala)
    }

    override def snapshotState(checkpointId: Long): util.List[ListSplit[T]] = {
      splitQueue.toList.asJava
    }

    override def notifyNoMoreSplits(): Unit = {}

    override def isAvailable: CompletableFuture[Void] = if (splitQueue.nonEmpty) {
      val executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
      CompletableFuture.supplyAsync(() => null, executor)
    } else {
      CompletableFuture.completedFuture(null)
    }

    override def close(): Unit = {}
  }
}
