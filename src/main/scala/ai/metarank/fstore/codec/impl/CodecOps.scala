package ai.metarank.fstore.codec.impl

import ai.metarank.util.VarNum
import java.io.{DataInput, DataOutput}

object CodecOps {
  extension (self: DataOutput) {
    def writeVarInt(value: Int): Unit   = VarNum.putVarInt(value, self)
    def writeVarLong(value: Long): Unit = VarNum.putVarLong(value, self)
  }

  extension (self: DataInput) {
    def readVarInt(): Int   = VarNum.getVarInt(self)
    def readVarLong(): Long = VarNum.getVarLong(self)
  }
}
