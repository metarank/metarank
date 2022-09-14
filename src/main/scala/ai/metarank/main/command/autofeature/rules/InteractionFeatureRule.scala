package ai.metarank.main.command.autofeature.rules

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.UserScopeType
import ai.metarank.util.Logging

object InteractionFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = for {
    interaction <- model.interactions.types.keys.toList
    field <- model.itemFields.strings.flatMap {
      case (name, stat) if (StringFeatureRule().fieldValues(stat).size > 1) => Some(name)
      case _                                                                => None
    }
  } yield {
    make(interaction, field)
  }

  def make(interaction: String, field: String): InteractedWithSchema = {
    logger.info(
      s"generated interacted_with feature for interaction '$interaction' over field '${field}'"
    )

    InteractedWithSchema(
      name = FeatureName(s"${interaction}_${field}"),
      interaction = interaction,
      field = FieldName(Item, field),
      scope = UserScopeType,
      count = None,
      duration = None
    )
  }

}
