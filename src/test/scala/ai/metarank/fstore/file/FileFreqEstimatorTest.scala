package ai.metarank.fstore.file

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.FreqEstimatorState
import ai.metarank.model.Write.PutFreqSample
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import scala.util.Random

class FileFreqEstimatorTest extends FreqEstimatorSuite with FileTest with Eventually {
  override def feature(config: FreqEstimatorConfig): FileFreqEstimatorFeature =
    FileFreqEstimatorFeature(config, db.sortedStringDB(config.name.value + Random.nextInt()), BinaryStoreFormat)

  it should "pull state" in {
    eventually {
      val c = config.copy(name = FeatureName("ffe" + Random.nextInt()))
      val f = feature(c)
      f.put(PutFreqSample(TestKey(c, "a"), now, "a")).unsafeRunSync()
      f.put(PutFreqSample(TestKey(c, "a"), now, "b")).unsafeRunSync()
      val state = FileFreqEstimatorFeature.fileFreqSource.source(f).compile.toList.unsafeRunSync()
      state.flatMap(_.values) should contain theSameElementsAs List("a", "b")
    }
  }
}
