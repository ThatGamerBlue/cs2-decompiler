package org.runestar.cs2.bin

import org.runestar.cs2.cg.intConstantToString
import org.runestar.cs2.ir.CATEGORY
import kotlin.math.abs

data class ScriptName(val trigger: Trigger, val name: String) {

    override fun toString() = "[$trigger,$name]"
}

fun ScriptName(cacheName: String): ScriptName {
    val n = cacheName.toIntOrNull()
    return if (n == null) {
        val comma = cacheName.indexOf(',')
        require(cacheName.startsWith('[') && cacheName.endsWith(']') && comma != -1)
        ScriptName(Trigger.valueOf(cacheName.substring(1, comma)), cacheName.substring(comma + 1, cacheName.length - 1))
    } else {
        var t = n + 512
        if (t in 0..255) {
            // global trigger
            ScriptName(Trigger.of(t), "_")
        } else {
            val c = abs((n shr 8) + 3)
            t = (c shl 8) + n + 768
            if (t in 0..255) {
                // category trigger
                ScriptName(Trigger.of(t), "_${intConstantToString(c, CATEGORY)}")
            } else {
                // type trigger
                val trigger = Trigger.of(n and 0xFF)
                val subjectType = trigger.subjectType ?: error("no subject type defined for $trigger")
                ScriptName(trigger, intConstantToString((n shr 8), subjectType))
            }
        }
    }
}