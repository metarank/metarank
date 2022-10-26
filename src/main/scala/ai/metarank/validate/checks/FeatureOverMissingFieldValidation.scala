package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.{Event, FeatureSchema, FieldName}
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.model.FieldName.EventType.{Interaction, Item, Ranking, User}
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError

object FeatureOverMissingFieldValidation extends EventValidation {
  case class FeatureOverMissingFieldError(features: Map[FeatureSchema, FieldName]) extends ValidationError
  case class State(features: List[FeatureSchema] = Nil)
  override def name: String = "field reference check"

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {

    val fields = events
      .collect {
        case e: ItemEvent        => e.fields.map(f => FieldName(Item, f.name))
        case e: UserEvent        => e.fields.map(f => FieldName(User, f.name))
        case e: RankingEvent     => e.fields.map(f => FieldName(Ranking, f.name))
        case e: InteractionEvent => e.fields.map(f => FieldName(Interaction(e.`type`), f.name))
      }
      .flatten
      .toSet

    val brokenRefs: Map[FeatureSchema, FieldName] = config.features.toList.flatMap {
      case f @ BooleanFeatureSchema(_, field, _, _, _) if !fields.contains(field) => List(f -> field)
      case f @ FieldMatchSchema(_, field, _, _, _, _) if !fields.contains(field)  => List(f -> field)
      case f @ FieldMatchSchema(_, _, field, _, _, _) if !fields.contains(field)  => List(f -> field)
      case f @ InteractedWithSchema(_, _, fieldSet, _, _, _, _, _) =>
        fieldSet.filter(f => !fields.contains(f)).map(field => f -> field)
      case f @ ItemAgeSchema(_, field, _, _) if !fields.contains(field)                => List(f -> field)
      case f @ NumberFeatureSchema(_, field, _, _, _) if !fields.contains(field)       => List(f -> field)
      case f @ RefererSchema(_, field, _, _, _) if !fields.contains(field)             => List(f -> field)
      case f @ StringFeatureSchema(_, field, _, _, _, _, _) if !fields.contains(field) => List(f -> field)
      case f @ UserAgentSchema(_, field, _, _, _) if !fields.contains(field)           => List(f -> field)
      case f @ WordCountSchema(_, field, _, _, _) if !fields.contains(field)           => List(f -> field)
    }.toMap
    if (brokenRefs.isEmpty) {
      logger.info(s"$name = PASS (${config.features.size} features referencing existing ${fields.size} event fields)")
      Nil
    } else {
      logger.warn(s"$name = FAIL (${brokenRefs.size} features reference non-existent event fields)")
      brokenRefs.foreach { case (conf, field) =>
        logger.warn(s"feature ${conf.name} references field $field, and no events with this field exists")
      }
      List(FeatureOverMissingFieldError(brokenRefs))
    }
  }
}
