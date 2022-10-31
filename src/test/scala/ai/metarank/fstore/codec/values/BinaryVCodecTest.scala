package ai.metarank.fstore.codec.values

import ai.metarank.fstore.codec.impl.ScalarCodec
import ai.metarank.model.Scalar
import ai.metarank.model.Scalar.SString

class BinaryVCodecTest extends VCodecTest[Scalar] {
  override val codec    = BinaryVCodec(compress = true, ScalarCodec)
  override val instance = SString("yolo")
}
