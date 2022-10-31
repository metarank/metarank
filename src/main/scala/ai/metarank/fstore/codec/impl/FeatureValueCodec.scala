package ai.metarank.fstore.codec.impl

import ai.metarank.model.Feature.MapFeature.MapConfig
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
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.{GlobalScope, ItemScope, SessionScope, UserScope}
import ai.metarank.model.{FeatureValue, Key, Scope, Timestamp}

import java.io.{DataInput, DataOutput}

object FeatureValueCodec extends BinaryCodec[FeatureValue] {
  import CodecOps._

  val mapIntDoubleCodec      = new MapCodec(BinaryCodec.int, BinaryCodec.double)
  val mapStringScalarCodec   = new MapCodec(BinaryCodec.string, ScalarCodec)
  val mapStringDoubleCodec   = new MapCodec(BinaryCodec.string, BinaryCodec.double)
  val listPeriodicValueCodec = new ListCodec(PeriodicValueCodec)
  val listTimeValueCodec     = new ListCodec(TimeValueCodec)

  override def read(in: DataInput): FeatureValue = in.readByte() match {
    case 0 =>
      ScalarValue(KeyCodec.read(in), Timestamp(in.readVarLong()), ScalarCodec.read(in))
    case 1 =>
      CounterValue(KeyCodec.read(in), Timestamp(in.readVarLong()), in.readVarLong())
    case 2 =>
      NumStatsValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        min = in.readDouble(),
        max = in.readDouble(),
        quantiles = mapIntDoubleCodec.read(in)
      )
    case 3 =>
      MapValue(KeyCodec.read(in), Timestamp(in.readVarLong()), mapStringScalarCodec.read(in))
    case 4 =>
      PeriodicCounterValue(KeyCodec.read(in), Timestamp(in.readVarLong()), listPeriodicValueCodec.read(in))
    case 5 =>
      FrequencyValue(KeyCodec.read(in), Timestamp(in.readVarLong()), mapStringDoubleCodec.read(in))
    case 6 =>
      BoundedListValue(KeyCodec.read(in), Timestamp(in.readVarLong()), listTimeValueCodec.read(in))
    case other =>
      throw new Exception(s"cannot decode fv index $other")
  }

  override def write(value: FeatureValue, out: DataOutput): Unit = value match {
    case FeatureValue.ScalarValue(key, ts, value) =>
      out.writeByte(0)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      ScalarCodec.write(value, out)

    case FeatureValue.CounterValue(key, ts, value) =>
      out.writeByte(1)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      out.writeVarLong(value)

    case FeatureValue.NumStatsValue(key, ts, min, max, quantiles) =>
      out.writeByte(2)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      out.writeDouble(min)
      out.writeDouble(max)
      mapIntDoubleCodec.write(quantiles, out)

    case FeatureValue.MapValue(key, ts, values) =>
      out.writeByte(3)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      mapStringScalarCodec.write(values, out)

    case FeatureValue.PeriodicCounterValue(key, ts, values) =>
      out.writeByte(4)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      listPeriodicValueCodec.write(values, out)

    case FeatureValue.FrequencyValue(key, ts, values) =>
      out.writeByte(5)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      mapStringDoubleCodec.write(values, out)

    case FeatureValue.BoundedListValue(key, ts, values) =>
      out.writeByte(6)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      listTimeValueCodec.write(values, out)
  }

  object PeriodicValueCodec extends BinaryCodec[PeriodicValue] {
    override def read(in: DataInput): PeriodicValue = {
      PeriodicValue(
        start = Timestamp(in.readVarLong()),
        end = Timestamp(in.readVarLong()),
        periods = in.readVarInt(),
        value = in.readVarLong()
      )
    }

    override def write(value: PeriodicValue, out: DataOutput): Unit = {
      out.writeVarLong(value.start.ts)
      out.writeVarLong(value.end.ts)
      out.writeVarInt(value.periods)
      out.writeVarLong(value.value)
    }
  }

  object KeyCodec extends BinaryCodec[Key] {
    override def read(in: DataInput): Key = {
      val scope = ScopeCodec.read(in)
      val name  = in.readUTF()
      Key(scope, FeatureName(name))
    }
    override def write(value: Key, out: DataOutput): Unit = {
      ScopeCodec.write(value.scope, out)
      out.writeUTF(value.feature.value)
    }
  }

  object ScopeCodec extends BinaryCodec[Scope] {
    override def read(in: DataInput): Scope = in.readByte() match {
      case 0     => UserScope(UserId(in.readUTF()))
      case 1     => ItemScope(ItemId(in.readUTF()))
      case 2     => GlobalScope
      case 3     => SessionScope(SessionId(in.readUTF()))
      case other => throw new Exception(s"cannot parse scope with index $other")
    }
    override def write(value: Scope, out: DataOutput): Unit = value match {
      case Scope.UserScope(user) =>
        out.writeByte(0)
        out.writeUTF(user.value)
      case Scope.ItemScope(item) =>
        out.writeByte(1)
        out.writeUTF(item.value)
      case Scope.GlobalScope =>
        out.writeByte(2)
      case Scope.SessionScope(session) =>
        out.writeByte(3)
        out.writeUTF(session.value)
    }
  }
}
