package ai.metarank.feature.matcher

import java.util
import java.util.Comparator

trait FieldMatcher {

  /** Should emit a lexicographically sorted array of tokens
    * @param string
    * @return
    */
  def tokenize(string: String): Array[String]

  def score(a: Array[String], b: Array[String]): Double = {
    if ((a.length == 0) || (b.length == 0)) {
      0.0
    } else {
      var i            = 0
      var j            = 0
      var union        = 0
      var intersection = 0
      while ((i < a.length) || (j < b.length)) {
        if ((i < a.length) && (j < b.length)) {
          if (a(i).compareTo(b(j)) == 0) {
            intersection += 1
            union += 1
            i += 1
            j += 1
          } else if (a(i).compareTo(b(j)) < 0) {
            union += 1
            i += 1
          } else {
            union += 1
            j += 1
          }
        } else {
          if (i < a.length) {
            union += 1
            i += 1
          } else {
            union += 1
            j += 1
          }
        }
      }
      intersection.toDouble / union.toDouble
    }
  }

  def unique(buffer: Array[String]): Array[String] = {
    util.Arrays.sort(buffer, Comparator.naturalOrder[String]())
    var pos = 0
    var i   = 1
    while (i < buffer.length) {
      if (buffer(pos) == buffer(i)) {
        i += 1
      } else {
        pos += 1
        buffer(pos) = buffer(i)
        i += 1
      }
    }
    if (pos + 1 == i) buffer else util.Arrays.copyOf(buffer, pos + 1)
  }

}
