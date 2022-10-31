package ai.metarank.fstore.codec.impl

import ai.metarank.rank.LambdaMARTModel.LambdaMARTScorer
import ai.metarank.rank.{LambdaMARTModel, NoopModel, ShuffleModel}
import ai.metarank.rank.Model.Scorer
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.rank.ShuffleModel.ShuffleScorer
import io.github.metarank.ltrlib.booster.{LightGBMBooster, XGBoostBooster}

import java.io.{DataInput, DataOutput}

object ScorerCodec extends BinaryCodec[Scorer] {
  import CodecOps._

  override def read(in: DataInput): Scorer = in.readByte() match {
    case 0 =>
      val booster = in.readByte() match {
        case 0     => LightGBMBooster(ByteArrayCodec.read(in))
        case 1     => XGBoostBooster(ByteArrayCodec.read(in))
        case other => throw new Exception(s"cannot decode booster with index $other")
      }
      LambdaMARTScorer(booster)
    case 1     => NoopScorer
    case 2     => ShuffleScorer(in.readVarInt())
    case other => throw new Exception(s"cannot decode scorer with index $other")
  }

  override def write(value: Scorer, out: DataOutput): Unit = value match {
    case LambdaMARTModel.LambdaMARTScorer(booster) =>
      out.writeByte(0)
      booster match {
        case LightGBMBooster(model, datasets) =>
          out.writeByte(0)
          ByteArrayCodec.write(booster.save(), out)
        case XGBoostBooster(model) =>
          out.writeByte(1)
          ByteArrayCodec.write(booster.save(), out)
        case other => throw new Exception(s"cannot encode booster $other")
      }
    case NoopModel.NoopScorer => out.writeByte(1)
    case ShuffleModel.ShuffleScorer(maxPositionChange) =>
      out.writeByte(2)
      out.writeVarInt(maxPositionChange)
    case other => throw new Exception(s"cannot encode scorer $other")
  }

  object ByteArrayCodec extends BinaryCodec[Array[Byte]] {
    override def read(in: DataInput): Array[Byte] = {
      val size   = in.readVarInt()
      val buffer = new Array[Byte](size)
      in.readFully(buffer)
      buffer
    }

    override def write(value: Array[Byte], out: DataOutput): Unit = {
      out.writeVarInt(value.length)
      out.write(value)
    }
  }
}
