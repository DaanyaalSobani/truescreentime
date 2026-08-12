package dev.daanyaal.truescreentime

/**
 * One wedge of the donut. [isOther] marks the pooled tail, which is the
 * only slice that can be drilled into.
 */
data class DonutSlice(
    val label: String,
    val totalMs: Long,
    val isOther: Boolean,
)

/**
 * Builds the donut's wedges: the biggest few apps individually, everything
 * else pooled into "Other" — or, when drilled in, the pooled tail spread
 * out on its own.
 */
object DonutSlices {

    const val TOP_SLICES = 4

    /** True when there is a tail worth drilling into. */
    fun hasOther(included: List<AppUsage>): Boolean = included.size > TOP_SLICES

    fun build(
        included: List<AppUsage>,
        otherLabel: String,
        drilledIn: Boolean,
    ): List<DonutSlice> {
        if (drilledIn) {
            return included.drop(TOP_SLICES).map {
                DonutSlice(it.label, it.totalTimeMs, isOther = false)
            }
        }

        val slices = included.take(TOP_SLICES).map {
            DonutSlice(it.label, it.totalTimeMs, isOther = false)
        }
        val otherMs = included.drop(TOP_SLICES).sumOf { it.totalTimeMs }
        return if (otherMs > 0) {
            slices + DonutSlice(otherLabel, otherMs, isOther = true)
        } else {
            slices
        }
    }
}
