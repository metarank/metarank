package ai.metarank.flow

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.{ItemValue, MValue}
import io.github.metarank.ltrlib.model.Feature.{CategoryFeature, SingularFeature, VectorFeature}
import io.github.metarank.ltrlib.model.{DatasetDescriptor, LabeledItem, Query}

object ClickthroughQuery {
  def apply(
      values: List[ItemValue],
      ints: List[TypedInteraction],
      id: Any,
      weights: Map[String, Double],
      dataset: DatasetDescriptor
  ): Query = {
    val items = for {
      item <- values
    } yield {
      LabeledItem(
        label = ints.find(_.item == item.id).flatMap(tpe => weights.get(tpe.tpe)).getOrElse(0.0),
        group = math.abs(id.hashCode),
        values = collectFeatureValues(dataset, item.values)
      )
    }
    Query(dataset, items)
  }

  def apply(
      values: List[ItemValue],
      id: Any,
      dataset: DatasetDescriptor
  ): Query = {
    val items = for {
      item <- values
    } yield {
      LabeledItem(
        label = 0.0,
        group = math.abs(id.hashCode),
        values = collectFeatureValues(dataset, item.values)
      )
    }
    Query(dataset, items)
  }

  def collectFeatureValues(dataset: DatasetDescriptor, values: List[MValue]): Array[Double] = {
    val buffer = new Array[Double](dataset.dim)
    for {
      value <- values if (dataset.dim > 0)
    } {
      value match {
        case MValue.SingleValue(name, value) =>
          dataset.offsets.get(SingularFeature(name.value)) match {
            case Some(o) => buffer(o) = value
            case None    => //
          }
        case MValue.VectorValue(name, values, dim) =>
          dataset.offsets.get(VectorFeature(name.value, dim.dim)) match {
            case Some(o) => System.arraycopy(values, 0, buffer, o, values.length)
            case None    => //
          }
        case MValue.CategoryValue(name, _, index) =>
          dataset.offsets.get(CategoryFeature(name.value)) match {
            case Some(o) => buffer(o) = index
            case None    => //
          }
      }
    }
    buffer
  }
}
