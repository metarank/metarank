package ai.metarank.mode

import ai.metarank.model.{Clickthrough, Event}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import io.findify.featury.model.{FeatureValue, Key, Scalar, State, Write}
import org.apache.flink.api.common.typeinfo.TypeInformation
import scala.language.higherKinds
import org.apache.flink.api.scala._

object TypeInfos {
  implicit lazy val stateInfo: TypeInformation[State]                  = createTypeInformation[State]
  implicit lazy val rankInfo: TypeInformation[RankingEvent]            = createTypeInformation[RankingEvent]
  implicit lazy val interactionInfo: TypeInformation[InteractionEvent] = createTypeInformation[InteractionEvent]
  implicit lazy val feedbackInfo: TypeInformation[FeedbackEvent]       = createTypeInformation[FeedbackEvent]
  implicit lazy val eventInfo: TypeInformation[Event]                  = createTypeInformation[Event]
  implicit lazy val clickthroughInfo: TypeInformation[Clickthrough]    = createTypeInformation[Clickthrough]
  implicit lazy val writeInfo: TypeInformation[Write]                  = createTypeInformation[Write]
  implicit lazy val keyInfo: TypeInformation[Key]                      = createTypeInformation[Key]
  implicit lazy val featureValueInfo: TypeInformation[FeatureValue]    = createTypeInformation[FeatureValue]
  implicit lazy val scalarInfo: TypeInformation[Scalar]                = createTypeInformation[Scalar]
}
