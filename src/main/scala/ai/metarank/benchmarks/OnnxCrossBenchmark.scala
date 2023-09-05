package ai.metarank.benchmarks
import ai.metarank.ml.onnx.ModelHandle.LocalModelHandle
import ai.metarank.ml.onnx.sbert.OnnxCrossEncoder.SentencePair
import ai.metarank.ml.onnx.sbert.{OnnxBiEncoder, OnnxCrossEncoder, OnnxSession}
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class OnnxCrossBenchmark {
  var encoder: OnnxCrossEncoder = _

  val pair = SentencePair(
    a = "hello, world",
    b =
      """Glass Mason Jars (12 Pack) - Regular Mouth Jam Jelly Jars,
        |Airtight Lid, USDA Approved Dishwasher Safe USA Made Pickling,
        |Preserving, Decorating, Canning Jar, Craft and Dry Food Storage (12 Ounce)""".stripMargin.replaceAll("\n", "")
  )

  @Param(Array("1", "10", "100"))
  var batch: String  = _
  var batchSize: Int = _

  @Param(Array("ce-msmarco-MiniLM-L6-v2"))
  var model: String = _

  @Setup
  def setup = {
    batchSize = batch.toInt
    val path = s"/home/shutty/code/metarank-huggingface/$model"
    val session =
      OnnxSession.loadFromLocalDir(LocalModelHandle(path), "pytorch_model.onnx", "vocab.txt").unsafeRunSync()
    encoder = OnnxCrossEncoder(session)
  }

  @Benchmark
  def encode() = {
    val pairs = Array.fill(batchSize)(pair)
    encoder.encode(pairs)
  }
}
