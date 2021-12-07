package ai.metarank.mode

import ai.metarank.model.{Clickthrough, Event}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import io.findify.featury.model.{FeatureValue, Key, Scalar, State, Write}
import io.findify.flinkadt.api._
import org.apache.flink.api.common.typeinfo.TypeInformation
import scala.language.higherKinds

object TypeInfos {
  implicit lazy val stateInfo: TypeInformation[State]                  = deriveTypeInformation[State]
  implicit lazy val rankInfo: TypeInformation[RankingEvent]            = deriveTypeInformation[RankingEvent]
  implicit lazy val interactionInfo: TypeInformation[InteractionEvent] = deriveTypeInformation[InteractionEvent]
  implicit lazy val feedbackInfo: TypeInformation[FeedbackEvent]       = deriveTypeInformation[FeedbackEvent]
  implicit lazy val eventInfo: TypeInformation[Event]                  = deriveTypeInformation[Event]
  implicit lazy val clickthroughInfo: TypeInformation[Clickthrough]    = deriveTypeInformation[Clickthrough]
  implicit lazy val writeInfo: TypeInformation[Write]                  = deriveTypeInformation[Write]
  implicit lazy val keyInfo: TypeInformation[Key]                      = deriveTypeInformation[Key]
  implicit lazy val featureValueInfo: TypeInformation[FeatureValue]    = deriveTypeInformation[FeatureValue]
  implicit lazy val scalarInfo: TypeInformation[Scalar]                = deriveTypeInformation[Scalar]
}
