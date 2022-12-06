package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.flow.ClickthroughJoinBuffer
import ai.metarank.fstore.clickthrough.FileClickthroughStore
import ai.metarank.fstore.codec.StoreFormat.{BinaryStoreFormat, JsonStoreFormat}
import ai.metarank.fstore.memory.{MemClickthroughStore, MemPersistence}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Timestamp
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.RanklensEvents
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RanklensFileCtsTest extends AnyFlatSpec with Matchers {
  val config = Config
    .load(IOUtils.resourceToString("/ranklens/config.yml", StandardCharsets.UTF_8), Map.empty)
    .unsafeRunSync()
  val mapping     = FeatureMapping.fromFeatureSchema(config.features, config.models).optimize()
  lazy val store  = MemPersistence(mapping.schema)
  lazy val file   = Files.createTempFile("cts", ".dat")
  lazy val cts    = FileClickthroughStore.create(file.toString, JsonStoreFormat).allocated.unsafeRunSync()._1
  val model       = mapping.models("xgboost").asInstanceOf[LambdaMARTModel]
  val modelConfig = config.models("xgboost").asInstanceOf[LambdaMARTConfig]
  lazy val buffer = ClickthroughJoinBuffer(ClickthroughJoinConfig(), store.values, cts, mapping)

  it should "import events" in {
    Import.slurp(fs2.Stream.emits(RanklensEvents()), store, mapping, buffer).unsafeRunSync()
    buffer.flushQueue(Timestamp.max).unsafeRunSync()
  }

  it should "train the xgboost model" in {
    Train.train(store, cts, model, "xgboost", modelConfig.backend, SplitStrategy.default).unsafeRunSync()
  }

}
