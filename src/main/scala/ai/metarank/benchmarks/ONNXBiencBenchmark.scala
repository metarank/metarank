package ai.metarank.benchmarks

import ai.metarank.ml.onnx.ModelHandle.LocalModelHandle
import ai.metarank.ml.onnx.sbert.{OnnxBiEncoder, OnnxSession}
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class ONNXBiencBenchmark {
  var encoder: OnnxBiEncoder = _

  val text =
    """Glass Mason Jars (12 Pack) - Regular Mouth Jam Jelly Jars,
      |Airtight Lid, USDA Approved Dishwasher Safe USA Made Pickling,
      |Preserving, Decorating, Canning Jar, Craft and Dry Food Storage (12 Ounce)""".stripMargin.replaceAll("\n", "")

  @Param(Array("all-MiniLM-L6-v2", "all-MiniLM-L12-v2", "all-mpnet-base-v2"))
  var model: String = _

  @Param(Array("1"))
  var batch: String  = _
  var batchSize: Int = _

  @Setup
  def setup(): Unit = {
    val path = s"/home/shutty/code/metarank-huggingface/$model"
    val session =
      OnnxSession.loadFromLocalDir(LocalModelHandle(path), "pytorch_model.onnx", "vocab.txt").unsafeRunSync()
    encoder = OnnxBiEncoder(session)
    batchSize = batch.toInt
  }

  @Benchmark
  def encode() = {
    val batch = Array.fill(batchSize)(text)
    encoder.embed(batch)
  }
}
