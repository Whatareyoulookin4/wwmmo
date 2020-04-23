package au.com.codeka.warworlds.server.util

import com.google.common.base.Objects

/**
 * Helper class that represents a pair of values. This can be used as the key to a dictionary,
 * for example.
 */
class ComparablePair<E : Comparable<E>, F : Comparable<F>>(var one: E, var two: F) : Comparable<Pair<E, F>> {
  override fun compareTo(other: Pair<E, F>): Int {
    var comp = one.compareTo(other.one!!)
    if (comp == 0) {
      comp = two.compareTo(other.two!!)
    }
    return comp
  }

  override fun hashCode(): Int {
    return Objects.hashCode(one, two)
  }

  override fun equals(obj: Any?): Boolean {
    if (obj == null || obj !is Pair<*, *>) {
      return false
    }
    val other: Pair<E, F> = obj as Pair<E, F>
    return Objects.equal(other.one, one) && Objects.equal(other.two, two)
  }
}