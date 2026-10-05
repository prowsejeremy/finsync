package com.jpd.finsync.home

private const val KEY_SEPARATOR = ","

/**
 * Which categories Home shows, and in what order (spec "Home customisation"). Plain Kotlin, so
 * the rules are tested on the JVM.
 */
data class HomeLayout(
    /** Every category once, in Home's order. */
    val order: List<HomeCategory>,
    val hidden: Set<HomeCategory>
) {
    /** The categories Home shows, in order. */
    val visible: List<HomeCategory> get() = order.filter { it !in hidden }

    /** The keys, comma-separated, as saved in "home_order". */
    fun orderValue(): String = order.joinToString(KEY_SEPARATOR) { it.key }

    /** False only for the last shown category: Home always shows at least one. */
    fun canHide(category: HomeCategory): Boolean = category !in visible || visible.size > 1

    /** With [category] hidden or shown; unchanged if that would hide the last shown one. */
    fun withHidden(category: HomeCategory, hidden: Boolean): HomeLayout = when {
        !hidden -> copy(hidden = this.hidden - category)
        canHide(category) -> copy(hidden = this.hidden + category)
        else -> this
    }

    /** With the category at [fromIndex] of [order] moved to [toIndex]. */
    fun moved(fromIndex: Int, toIndex: Int): HomeLayout {
        if (fromIndex !in order.indices || toIndex !in order.indices) return this
        val newOrder = order.toMutableList()
        newOrder.add(toIndex, newOrder.removeAt(fromIndex))
        return copy(order = newOrder)
    }

    companion object {
        /** All six in today's order, all shown. */
        val DEFAULT = HomeLayout(HomeCategory.entries.toList(), emptySet())
    }
}

/**
 * Reads the saved "home_order" and "home_hidden" values. Unknown and repeated keys are dropped,
 * a category missing from the order joins the end (shown), and a hidden set covering every
 * category is cleared. Null values give [HomeLayout.DEFAULT].
 */
fun homeLayoutOf(orderValue: String?, hiddenKeys: Set<String>?): HomeLayout {
    val saved = orderValue.orEmpty()
        .split(KEY_SEPARATOR)
        .mapNotNull { HomeCategory.fromKey(it) }
        .distinct()
    val order = saved + HomeCategory.entries.filter { it !in saved }
    // Only categories from the saved order can be hidden, so one that joins the end is shown.
    val hidden = hiddenKeys.orEmpty()
        .mapNotNull { HomeCategory.fromKey(it) }
        .filterTo(HashSet()) { it in saved }
    return HomeLayout(order, if (order.all { it in hidden }) emptySet() else hidden)
}
