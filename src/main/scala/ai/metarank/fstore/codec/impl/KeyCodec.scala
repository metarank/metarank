package ai.metarank.fstore.codec.impl

import ai.metarank.fstore.codec.KCodec
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Key, Scope}

object KeyCodec extends KCodec[Key] {
  override def encode(prefix: String, value: Key): String = s"$prefix/${encodeNoPrefix(value)}"

  override def decodeNoPrefix(str: String): Either[Throwable, Key] = {
    val slashPos = str.indexOf('/')
    if (slashPos > 0) {
      val featureName      = str.substring(0, slashPos)
      val scopeValueString = str.substring(slashPos + 1)
      ScopeCodec.decode(scopeValueString).map(s => Key(s, FeatureName(featureName)))
    } else {
      Left(new Exception(s"cannot parse key $str"))
    }
  }

  override def encodeNoPrefix(value: Key): String = s"${value.feature.value}/${value.scope.asString}"

  override def decode(str: String): Either[Throwable, Key] = {
    val prefixPos = str.indexOf('/')
    if (prefixPos > 0) {
      // val prefix = str.substring(0, prefixPos)
      val key = str.substring(prefixPos + 1)
      decodeNoPrefix(key)
    } else {
      Left(new Exception(s"cannot parse key $str"))
    }
  }
}
