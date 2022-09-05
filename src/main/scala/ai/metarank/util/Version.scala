package ai.metarank.util

import org.apache.commons.io.IOUtils

import java.io.ByteArrayInputStream
import scala.util.Try

object Version {
  def apply() = {
    val version = for {
      bytes <- Try(IOUtils.resourceToByteArray("META-INF/MANIFEST.MF")).toOption
      manifest = new java.util.jar.Manifest(new ByteArrayInputStream(bytes))
      attrs <- Option(manifest.getMainAttributes)
      version <- Option(attrs.get("versionName")).flatMap {
        case s: String => Some(s)
        case _         => None
      }
    } yield {
      version
    }
    version
  }

  def isRelease: Boolean = apply().isDefined
}
