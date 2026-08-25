package ai.metarank.fstore.codec.impl

import java.nio.file.{Files, Paths}

/** Generator for the golden fixtures in src/test/resources/codec/ used by BinaryCodecFixtureTest. Run with `sbt
  * "Test/runMain ai.metarank.fstore.codec.impl.BinaryFixtureGen"` from the repo root. Only regenerate when a new format
  * version is introduced deliberately: overwriting an existing fixture hides a break of persisted-state compatibility.
  */
object BinaryFixtureGen {
  def main(args: Array[String]): Unit = {
    import BinaryCodecFixtureTest.*
    val dir = Paths.get("src/test/resources/codec")
    Files.write(dir.resolve("fv-v2.bin"), encodeAll(FeatureValueCodec, featureValues))
    Files.write(dir.resolve("scalar-v1.bin"), encodeAll(ScalarCodec, scalars))
    Files.write(dir.resolve("mvalue-v1.bin"), encodeAll(MValueCodec, mvalues))
    println("fixtures written")
  }
}
