package ai.metarank.util

abstract class DelimitedPair(sep: Char) {
  def unapply(string: String): Option[(String, String)] = {
    val split = string.indexOf(sep.toInt)
    if ((split < 1) || (split == string.length - 1)) {
      None
    } else {
      val left  = string.substring(0, split)
      val right = string.substring(split + 1)
      Some(left -> right)
    }
  }

}

object DelimitedPair {
  object DotDelimitedPair    extends DelimitedPair('.')
  object SlashDelimitedPair  extends DelimitedPair('/')
  object EqualsDelimitedPair extends DelimitedPair('=')
}
