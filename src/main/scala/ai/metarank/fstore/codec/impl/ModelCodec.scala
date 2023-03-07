package ai.metarank.fstore.codec.impl

import ai.metarank.ml.Model
import ai.metarank.ml.rank.{LambdaMARTRanker, NoopRanker, ShuffleRanker}
import ai.metarank.ml.recommend.RandomRecommender

import java.io.{DataInput, DataOutput}

object ModelCodec extends BinaryCodec[Model[_]] {
  val BITSTREAM_VERSION = 1
  import CodecOps._
  override def read(in: DataInput): Model[_] = ???

  override def write(value: Model[_], out: DataOutput): Unit = {
    out.writeByte(BITSTREAM_VERSION)
    value match {
      case LambdaMARTRanker.LambdaMARTModel(name, conf, booster) => ???
      case NoopRanker.NoopModel(name, config)                    => ???
      case RandomRecommender.RandomModel(name, items)            => ???
      case ShuffleRanker.ShuffleModel(name, config)              => ???
      case _                                                     => ???
    }
  }
}
