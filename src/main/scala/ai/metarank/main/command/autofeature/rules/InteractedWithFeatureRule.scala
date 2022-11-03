package ai.metarank.main.command.autofeature.rules

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.{FeatureSchema, FieldName}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.UserScopeType
import ai.metarank.util.Logging

object InteractedWithFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = for {
    interaction <- model.interactions.types.keys.toList
  } yield {
    val fields = model.itemFields.strings.flatMap {
      case (name, stat) if stat.values.size > 1 => Some(name)
      case _                                    => None
    }
    make(interaction, fields.toList)
  }

  def make(interaction: String, fields: List[String]): InteractedWithSchema = {
    logger.info(
      s"generated interacted_with feature for interaction '$interaction' over fields '${fields.mkString(",")}'"
    )

    InteractedWithSchema(
      name = FeatureName(interaction),
      interaction = interaction,
      field = fields.map(field => FieldName(Item, field)),
      scope = UserScopeType,
      count = None,
      duration = None
    )
  }

}
