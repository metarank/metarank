package ai.metarank.util

import ai.metarank.util.ListSource.{ListSplit, ListSplitCheckpointSerializer, ListSplitSerializer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.flinkadt.api._
import org.apache.flink.api.common.eventtime.WatermarkStrategy

class ListSourceTest extends AnyFlatSpec with Matchers with FlinkTest {
  lazy val splitSerializer = ListSplitSerializer(intSerializer)
  val checkpointSerializer = ListSplitCheckpointSerializer(intSerializer)

  "split serializer" should "do roundtrip" in {
    val split   = ListSplit(List(1, 2, 3, 4, 5))
    val bytes   = splitSerializer.serialize(split)
    val decoded = splitSerializer.deserialize(1, bytes)
    decoded shouldBe split
  }

  it should "handle nil" in {
    val decoded = splitSerializer.deserialize(1, splitSerializer.serialize(ListSplit(Nil)))
    decoded shouldBe ListSplit(Nil)
  }

  "split checkpoint serializer" should "do roundtrip" in {
    val checkpoint = List(ListSplit(List(1, 2)), ListSplit(List(3)), ListSplit(List(4, 5)))
    val bytes      = checkpointSerializer.serialize(checkpoint)
    val decoded    = checkpointSerializer.deserialize(1, bytes)
    decoded shouldBe checkpoint
  }

  it should "handle nil" in {
    val decoded = checkpointSerializer.deserialize(1, checkpointSerializer.serialize(Nil))
    decoded shouldBe Nil
  }

  it should "emit ints in small batches" in {
    val result = env
      .fromSource(ListSource(List(1, 2, 3, 4, 5)), WatermarkStrategy.noWatermarks(), "ints")
      .setParallelism(3)
      .executeAndCollect(100)
    result should contain theSameElementsAs List(1, 2, 3, 4, 5)
  }
}
