package ai.metarank.fstore.codec.impl

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.FeatureValue.PeriodicCounterValue.PeriodicValue
import ai.metarank.model.FeatureValue.{
  BoundedListValue,
  CounterValue,
  FrequencyValue,
  MapValue,
  NumStatsValue,
  PeriodicCounterValue,
  ScalarValue
}
import ai.metarank.model.Identifier.{ItemId, RankingId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.Scalar.{SBoolean, SDouble, SDoubleList, SString, SStringList}
import ai.metarank.model.Scope.{
  GlobalScope,
  ItemFieldScope,
  ItemScope,
  RankingFieldScope,
  RankingScope,
  SessionScope,
  UserScope
}
import ai.metarank.model.{FeatureValue, Key, MValue, Scalar, Timestamp}
import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import scala.concurrent.duration.*

/** Golden-fixture tests pinning the current on-disk binary format. Round-trip tests cannot catch an accidental change
  * of a tag byte (encode and decode change together), so these assert against reference bytes committed to
  * src/test/resources/codec/. If one of these tests fails, persisted state written by previous Metarank versions cannot
  * be read back anymore: never regenerate the fixtures to make the test pass, fix the codec instead.
  */
class BinaryCodecFixtureTest extends AnyFlatSpec with Matchers {
  import BinaryCodecFixtureTest.*

  it should "encode feature values into the reference bytes" in {
    encodeAll(FeatureValueCodec, featureValues) should contain theSameElementsInOrderAs
      IOUtils.resourceToByteArray("/codec/fv-v2.bin")
  }

  it should "decode reference feature values" in {
    decodeAll(FeatureValueCodec, IOUtils.resourceToByteArray("/codec/fv-v2.bin"), featureValues.size) shouldBe
      featureValues
  }

  it should "encode scalars into the reference bytes" in {
    encodeAll(ScalarCodec, scalars) should contain theSameElementsInOrderAs
      IOUtils.resourceToByteArray("/codec/scalar-v1.bin")
  }

  it should "decode reference scalars" in {
    decodeAll(ScalarCodec, IOUtils.resourceToByteArray("/codec/scalar-v1.bin"), scalars.size) shouldBe scalars
  }

  it should "encode mvalues into the reference bytes" in {
    encodeAll(MValueCodec, mvalues) should contain theSameElementsInOrderAs
      IOUtils.resourceToByteArray("/codec/mvalue-v1.bin")
  }

  it should "decode reference mvalues" in {
    decodeAll(MValueCodec, IOUtils.resourceToByteArray("/codec/mvalue-v1.bin"), mvalues.size) shouldBe mvalues
  }
}

object BinaryCodecFixtureTest {
  val ts     = Timestamp.date(2023, 1, 10, 12, 0, 0)
  val expire = 30.days

  // covers all current-format FeatureValue tags (7-13) and all binary Scope tags (0-6)
  val featureValues: List[FeatureValue] = List(
    ScalarValue(Key(ItemScope(ItemId("p1")), FeatureName("f1")), ts, SString("hello"), expire),
    CounterValue(Key(UserScope(UserId("u1")), FeatureName("f2")), ts, 42L, expire),
    NumStatsValue(Key(SessionScope(SessionId("s1")), FeatureName("f3")), ts, 1.0, 2.0, Map(50 -> 1.5), expire),
    MapValue(Key(GlobalScope, FeatureName("f4")), ts, Map("foo" -> SString("bar")), expire),
    PeriodicCounterValue(
      Key(ItemFieldScope("color", "red"), FeatureName("f5")),
      ts,
      Array(PeriodicValue(ts, Timestamp.date(2023, 1, 11, 12, 0, 0), 3, 7L)),
      expire
    ),
    FrequencyValue(Key(RankingFieldScope("q", "jeans", ItemId("p2")), FeatureName("f6")), ts, Map("a" -> 0.5), expire),
    BoundedListValue(
      Key(RankingScope(RankingId("r1")), FeatureName("f7")),
      ts,
      List(TimeValue(ts, SString("x"))),
      expire
    )
  )

  // covers all Scalar tags (0-4)
  val scalars: List[Scalar] = List(
    SString("hello"),
    SDouble(1.5),
    SBoolean(true),
    SStringList(List("a", "b")),
    SDoubleList(Array(1.0, 2.0))
  )

  // covers all MValue tags (0-2)
  val mvalues: List[MValue] = List(
    SingleValue(FeatureName("f1"), 1.0),
    VectorValue(FeatureName("f2"), Array(1.0, 2.0), 2),
    CategoryValue(FeatureName("f3"), "x", 1)
  )

  def encodeAll[T](codec: BinaryCodec[T], values: List[T]): Array[Byte] = {
    val buffer = new ByteArrayOutputStream()
    val out    = new DataOutputStream(buffer)
    values.foreach(value => codec.write(value, out))
    buffer.toByteArray
  }

  def decodeAll[T](codec: BinaryCodec[T], bytes: Array[Byte], count: Int): List[T] = {
    val in = new DataInputStream(new ByteArrayInputStream(bytes))
    (0 until count).map(_ => codec.read(in)).toList
  }
}
