package org.runestar.cs2.dfa

import org.runestar.cs2.SCRIPT_ARGS
import org.runestar.cs2.ir.*
import org.runestar.cs2.ir.Function

object ReorderArgs : Phase.Individual() {

    override fun transform(f: Function, fs: FunctionSet) {
        val oldStackTypes = f.arguments.map { it.stackType }
        val typing = fs.typings.args(f.id, oldStackTypes)
        val manualArguments: List<Prototype>? = SCRIPT_ARGS.load(f.id)
        val newStackTypes = manualArguments?.map { it.type.stackType } ?: typing.map { it.stackType }
        if (manualArguments != null) {
            require(typing.size == manualArguments.size) { "argument override out of date for script: ${f.id}, ${typing.size} != ${manualArguments.size}" }
            val temporaryTypings = typing.toMutableList()
            val reorderedTypings = newStackTypes.map { st -> temporaryTypings.removeAt(temporaryTypings.indexOfFirst { it.stackType == st }) }
            reorderedTypings.forEachIndexed { index, type -> type.freeze(manualArguments[index]) }
        }

        // TODO temporary and I am not proud of this
        // force [proc,max] to return an int
        if (f.id == 1045) {
            val test = fs.typings.returns(f.id, f.returnTypes)
            for (t in test) {
                t.freeze(INT)
            }
        }

        if (oldStackTypes == newStackTypes) return
        val oldArgs = f.arguments.toMutableList()
        val newArgs = newStackTypes.map { st -> oldArgs.removeAt(oldArgs.indexOfFirst { it.stackType == st }) }
        f.arguments = newArgs
    }
}