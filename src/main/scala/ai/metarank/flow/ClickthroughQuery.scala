package ai.metarank.flow

import ai.metarank.model.ItemValue
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.MValue
import io.github.metarank.ltrlib.model.{DatasetDescriptor, LabeledItem, Query}

object ClickthroughQuery {
  def apply(values: List[ItemValue], label: Double, id: String, dataset: DatasetDescriptor) = {
    val items = for {
      item <- values
    } yield {
      LabeledItem(
        label = label,
        group = math.abs(id.hashCode),
        values = item.values.flatMap {
          case MValue.SingleValue(_, value)      => List(value)
          case MValue.CategoryValue(_, _, index) => List(index.toDouble)
          case MValue.VectorValue(_, values, _)  => values
        }.toArray
      )
    }
    Query(dataset, items)
  }
}
