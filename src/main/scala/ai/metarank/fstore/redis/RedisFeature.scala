package ai.metarank.fstore.redis

import ai.metarank.model.Key

trait RedisFeature {
  def prefix: String

  def str(key: Key) = s"$prefix/${key.scope.asString}/${key.feature.value}"
}
