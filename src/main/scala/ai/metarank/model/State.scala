package ai.metarank.model

import ai.metarank.model.State.BoundedListState.TimeValue
import ai.metarank.model.State.PeriodicCounterState.TimeCounter
import io.circe._
import io.circe.generic.semiauto._

sealed trait State {
  def key: Key
  def ts: Timestamp
}

object State {
  case class ScalarState(key: Key, ts: Timestamp, value: Scalar)                      extends State
  case class CounterState(key: Key, ts: Timestamp, value: Long)                       extends State
  case class MapState(key: Key, ts: Timestamp, values: Map[String, Scalar])           extends State
  case class PeriodicCounterState(key: Key, ts: Timestamp, values: List[TimeCounter]) extends State
  object PeriodicCounterState {
    case class TimeCounter(ts: Timestamp, count: Long)
  }
  case class BoundedListState(key: Key, ts: Timestamp, values: List[TimeValue]) extends State
  object BoundedListState {
    case class TimeValue(ts: Timestamp, value: Scalar)
  }
  case class FrequencyState(key: Key, ts: Timestamp, values: List[String]) extends State
  case class StatsState(key: Key, ts: Timestamp, values: Array[Double])    extends State

  implicit val scalarStateCodec: Codec[ScalarState]                   = deriveCodec[ScalarState]
  implicit val counterStateCodec: Codec[CounterState]                 = deriveCodec[CounterState]
  implicit val mapStateCodec: Codec[MapState]                         = deriveCodec[MapState]
  implicit val timeCounterCodec: Codec[TimeCounter]                   = deriveCodec[TimeCounter]
  implicit val timeValueCodec: Codec[TimeValue]                       = deriveCodec[TimeValue]
  implicit val periodicCounterStateCodec: Codec[PeriodicCounterState] = deriveCodec[PeriodicCounterState]
  implicit val boundedListStateCodec: Codec[BoundedListState]         = deriveCodec[BoundedListState]
  implicit val freqStateCodec: Codec[FrequencyState]                  = deriveCodec[FrequencyState]
  implicit val statsStateCodec: Codec[StatsState]                     = deriveCodec[StatsState]

  implicit val stateEncoder: Encoder[State] = Encoder.instance {
    case s: ScalarState          => scalarStateCodec(s).deepMerge(tpe("scalar"))
    case s: CounterState         => counterStateCodec(s).deepMerge(tpe("counter"))
    case s: MapState             => mapStateCodec(s).deepMerge(tpe("map"))
    case s: PeriodicCounterState => periodicCounterStateCodec(s).deepMerge(tpe("per-counter"))
    case s: BoundedListState     => boundedListStateCodec(s).deepMerge(tpe("bounded-list"))
    case s: FrequencyState       => freqStateCodec(s).deepMerge(tpe("freq"))
    case s: StatsState           => statsStateCodec(s).deepMerge(tpe("stats"))
  }

  implicit val stateDecoder: Decoder[State] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      state <- tpe match {
        case "scalar"       => scalarStateCodec.tryDecode(c)
        case "counter"      => counterStateCodec.tryDecode(c)
        case "map"          => mapStateCodec.tryDecode(c)
        case "per-counter"  => periodicCounterStateCodec.tryDecode(c)
        case "bounded-list" => boundedListStateCodec.tryDecode(c)
        case "freq"         => freqStateCodec.tryDecode(c)
        case "stats"        => statsStateCodec.tryDecode(c)
        case other          => Left(DecodingFailure(s"state type $other not supported", c.history))
      }
    } yield state
  )

  implicit val stateCodec: Codec[State] = Codec.from(stateDecoder, stateEncoder)

  def tpe(t: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(t))))

}
