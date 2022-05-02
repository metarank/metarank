package ai.metarank.source.rest

import org.apache.flink.api.connector.source.SourceSplit

case class RestSplit(splitId: String, limit: Option[Long]) extends SourceSplit {
  def isFinished = limit match {
    case None    => false
    case Some(0) => true
    case Some(_) => false
  }
  def decrement(cnt: Int) = limit match {
    case None      => this
    case Some(lim) => copy(limit = Some(lim - cnt))
  }
}
