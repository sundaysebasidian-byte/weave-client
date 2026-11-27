package io.weave.client.subscription

private class FoldedName<T>(val value: T, val key: String)

/** Keep the persisted ordering, including stable ties, without case-folding on every comparison. */
internal fun <T> List<T>.sortedByFoldedName(name: (T) -> String): List<T> {
    if (size < 32) return sortedBy { name(it).lowercase() }
    val entries = ArrayList<FoldedName<T>>(size)
    for (value in this) entries.add(FoldedName(value, name(value).lowercase()))
    entries.sortBy { it.key }
    return entries.map { it.value }
}
