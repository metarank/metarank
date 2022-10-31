package ai.metarank.fstore.codec.impl

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Timestamp

import java.io.{DataInput, DataOutput}

object TimeValueCodec extends BinaryCodec[TimeValue] {
  import CodecOps._
  override def read(in: DataInput): TimeValue = {
    val ts     = Timestamp(in.readVarLong())
    val scalar = ScalarCodec.read(in)
    TimeValue(ts, scalar)
  }

  override def write(value: TimeValue, out: DataOutput): Unit = {
    out.writeVarLong(value.ts.ts)
    ScalarCodec.write(value.value, out)
  }
}
