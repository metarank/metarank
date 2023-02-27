package ai.metarank.validate.checks

import ai.metarank.config.Config
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.{ItemEvent, UserEvent}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.EventType.{Item, User}
import ai.metarank.model.{Event, FeatureSchema, Field, FieldName}
import ai.metarank.validate.EventValidation
import ai.metarank.validate.EventValidation.ValidationError
import ai.metarank.validate.checks.StringValuesValidation.StringValuesValidationError

trait StringValuesValidation extends EventValidation {
  def collectFieldValues(schema: FeatureSchema): Option[(String, List[String])]
  def collectEventFields(e: Event): List[(String, String)]

  override def validate(config: Config, events: List[Event]): List[EventValidation.ValidationError] = {
    val fieldValues =
      config.features.toList.flatMap(s => collectFieldValues(s)).groupMapReduce(_._1)(_._2)((l, r) => (l ++ r).distinct)
    val fieldUsage = events.flatMap(collectEventFields).groupMap(_._1)(_._2).map { case (field, values) =>
      field -> values.groupMapReduce(identity)(_ => 1)(_ + _)
    }
    val aggregatedUsage = (for {
      (field, values) <- fieldValues
      used            <- fieldUsage.get(field)
    } yield {
      field -> 100.0 * used.size.toDouble / values.size
    }).toList
      .sortBy(-_._2)
    if (fieldValues.nonEmpty) {
      val lowUsage = aggregatedUsage.filter(_._2 < 50)
      if (lowUsage.nonEmpty) {
        logger.warn(s"$name = WARN (fields ${lowUsage.map(_._1)} have too many unused values)")
        lowUsage.map { case (field, percent) =>
          val definedSize = fieldValues.get(field).map(_.size).getOrElse(0)
          val usedSize    = fieldUsage.get(field).map(_.size).getOrElse(0)
          logger.warn(s"field $field: $percent% usage ($definedSize defined in config, only $usedSize used)")
          StringValuesValidationError(field, percent)
        }
      } else {
        val stringified = aggregatedUsage.map { case (field, percent) => s"$field:$percent%" }
        logger.info(s"$name = PASS (usage distribution: $stringified)")
        Nil
      }
    } else {
      Nil
    }
  }
}

object StringValuesValidation {
  case class StringValuesValidationError(field: String, usagePercent: Double) extends ValidationError
  object ItemStringValuesValidation extends StringValuesValidation {
    override def name = "item string values usage"

    override def collectFieldValues(schema: FeatureSchema): Option[(String, List[String])] = schema match {
      case StringFeatureSchema(_, FieldName(Item, field), _, _, values, _, _) => Some(field -> values.toList)
      case _                                                                  => None
    }

    override def collectEventFields(e: Event): List[(String, String)] = e match {
      case e: ItemEvent =>
        e.fields.flatMap {
          case StringField(name, value)      => List(name -> value)
          case StringListField(name, values) => values.map(value => name -> value)
          case _                             => Nil
        }
      case _ => Nil
    }
  }

  object UserStringValuesValidation extends StringValuesValidation {
    override def name = "user string values usage"

    override def collectFieldValues(schema: FeatureSchema): Option[(String, List[String])] = schema match {
      case StringFeatureSchema(_, FieldName(User, field), _, _, values, _, _) => Some(field -> values.toList)
      case _                                                                  => None
    }

    override def collectEventFields(e: Event): List[(String, String)] = e match {
      case e: UserEvent =>
        e.fields.flatMap {
          case StringField(name, value)      => List(name -> value)
          case StringListField(name, values) => values.map(value => name -> value)
          case _                             => Nil
        }
      case _ => Nil
    }
  }

}
