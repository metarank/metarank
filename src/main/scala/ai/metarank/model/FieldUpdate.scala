package ai.metarank.model

case class FieldUpdate(id: Identifier, name: FieldName, field: Field)

object FieldUpdate {
  def fromEvent(event: Event) = event match {
    case event: Event.MetadataEvent => ???
    case event: Event.FeedbackEvent => ???
  }
}
