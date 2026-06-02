@file:JvmName("DefaultMapExtensions")

package com.willfp.eco.core.map

/**
 * Required to avoid type ambiguity.
 *
 * @see ListMap
 */
class MutableListMap<K : Any, V> : ListMap<K, V>() {
    /**
     * Override with enforced MutableList type.
     */
    override fun get(key: K?): MutableList<V> {
        val value = super.get(key)

        if (value is MutableList<V>) {
            return value
        }

        return value.toMutableList()
    }

    /**
     * Override with enforced MutableList type.
     */
    override fun getOrDefault(key: K, defaultValue: MutableList<V>): MutableList<V> {
        val value = super.getOrDefault(key, defaultValue)

        if (value is MutableList<V>) {
            return value
        }

        return value.toMutableList()
    }
}

/**
 * @see DefaultMap
 */
fun <K : Any, V : Any> defaultMap(defaultValue: V) =
    DefaultMap<K, V>(defaultValue)

/**
 * @see DefaultMap
 */
fun <K : Any, V : Any> defaultMap(defaultValue: () -> V) =
    DefaultMap<K, V>(defaultValue())

/**
 * @see ListMap
 */
fun <K : Any, V : Any> listMap() =
    MutableListMap<K, V>()

/**
 * @see DefaultMap.createNestedMap
 */
fun <K : Any, K1 : Any, V> nestedMap() =
    DefaultMap.createNestedMap<K, K1, V>()

/**
 * @see DefaultMap.createNestedListMap
 */
fun <K : Any, K1 : Any, V> nestedListMap() =
    DefaultMap<K, MutableListMap<K1, V>> {
        MutableListMap()
    }