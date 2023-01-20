package ai.metarank.fstore.file

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.State.MapState
import ai.metarank.model.Write.{Put, PutTuple}
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class FileMapFeatureTest extends MapFeatureSuite with FileTest {
  override def feature(config: MapConfig): FileMapFeature = FileMapFeature(config, db, "x", BinaryStoreFormat)

  it should "pull state" in {
    val c = config.copy(name = FeatureName("ms"))
    val f = feature(c)
    f.put(PutTuple(TestKey(c, "a"), now, "foo", Some(SString("bar")))).unsafeRunSync()
    f.put(PutTuple(TestKey(c, "a"), now, "baz", Some(SString("bar")))).unsafeRunSync()
    val state = FileMapFeature.mapStateSource.source(f).compile.toList.unsafeRunSync()
    state shouldBe List(MapState(TestKey(c, "a"), Map("foo" -> SString("bar"), "baz" -> SString("bar"))))
  }
}
