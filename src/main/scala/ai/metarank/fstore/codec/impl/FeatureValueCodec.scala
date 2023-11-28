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
import ai.metarank.model.Scope.{FieldScope, GlobalScope, ItemScope, SessionScope, UserScope}
import ai.metarank.model.{FeatureValue, Key, Scope, Timestamp}

import scala.concurrent.duration._
import java.io.{DataInput, DataOutput}
import java.util.concurrent.TimeUnit

object FeatureValueCodec extends BinaryCodec[FeatureValue] {
  import CodecOps._

  val mapIntDoubleCodec       = new MapCodec(BinaryCodec.int, BinaryCodec.double)
  val mapStringScalarCodec    = new MapCodec(BinaryCodec.string, ScalarCodec)
  val mapStringDoubleCodec    = new MapCodec(BinaryCodec.string, BinaryCodec.double)
  val arrayPeriodicValueCodec = new ArrayCodec(PeriodicValueCodec)
  val listTimeValueCodec      = new ListCodec(TimeValueCodec)

  override def read(in: DataInput): FeatureValue = in.readByte() match {
    case 0 => // compat
      ScalarValue(KeyCodec.read(in), Timestamp(in.readVarLong()), ScalarCodec.read(in), 90.days)
    case 7 =>
      ScalarValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        ScalarCodec.read(in),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 1 =>
      CounterValue(KeyCodec.read(in), Timestamp(in.readVarLong()), in.readVarLong(), 90.days)
    case 8 =>
      CounterValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        in.readVarLong(),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 2 => // compat
      NumStatsValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        min = in.readDouble(),
        max = in.readDouble(),
        quantiles = mapIntDoubleCodec.read(in),
        expire = 90.days
      )
    case 9 =>
      NumStatsValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        min = in.readDouble(),
        max = in.readDouble(),
        quantiles = mapIntDoubleCodec.read(in),
        expire = FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 3 => // compat
      MapValue(KeyCodec.read(in), Timestamp(in.readVarLong()), mapStringScalarCodec.read(in), 90.days)
    case 10 =>
      MapValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        mapStringScalarCodec.read(in),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 4 => // compat
      PeriodicCounterValue(KeyCodec.read(in), Timestamp(in.readVarLong()), arrayPeriodicValueCodec.read(in), 90.days)
    case 11 =>
      PeriodicCounterValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        arrayPeriodicValueCodec.read(in),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 5 => // compat
      FrequencyValue(KeyCodec.read(in), Timestamp(in.readVarLong()), mapStringDoubleCodec.read(in), 90.days)
    case 12 =>
      FrequencyValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        mapStringDoubleCodec.read(in),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case 6 => // compat
      BoundedListValue(KeyCodec.read(in), Timestamp(in.readVarLong()), listTimeValueCodec.read(in), 90.days)
    case 13 =>
      BoundedListValue(
        KeyCodec.read(in),
        Timestamp(in.readVarLong()),
        listTimeValueCodec.read(in),
        FiniteDuration(in.readVarLong(), TimeUnit.MILLISECONDS)
      )
    case other =>
      throw new Exception(s"cannot decode fv index $other")
  }

  override def write(value: FeatureValue, out: DataOutput): Unit = value match {
    case FeatureValue.ScalarValue(key, ts, value, expire) =>
      out.writeByte(7)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      ScalarCodec.write(value, out)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.CounterValue(key, ts, value, expire) =>
      out.writeByte(8)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      out.writeVarLong(value)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.NumStatsValue(key, ts, min, max, quantiles, expire) =>
      out.writeByte(9)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      out.writeDouble(min)
      out.writeDouble(max)
      mapIntDoubleCodec.write(quantiles, out)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.MapValue(key, ts, values, expire) =>
      out.writeByte(10)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      mapStringScalarCodec.write(values, out)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.PeriodicCounterValue(key, ts, values, expire) =>
      out.writeByte(11)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      arrayPeriodicValueCodec.write(values, out)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.FrequencyValue(key, ts, values, expire) =>
      out.writeByte(12)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      mapStringDoubleCodec.write(values, out)
      out.writeVarLong(expire.toMillis)

    case FeatureValue.BoundedListValue(key, ts, values, expire) =>
      out.writeByte(13)
      KeyCodec.write(key, out)
      out.writeVarLong(ts.ts)
      listTimeValueCodec.write(values, out)
      out.writeVarLong(expire.toMillis)
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
      case 4     => FieldScope(in.readUTF(), in.readUTF())
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
      case Scope.FieldScope(fieldName, fieldValue) =>
        out.writeByte(4)
        out.writeUTF(fieldName)
        out.writeUTF(fieldValue)
    }
  }
}
