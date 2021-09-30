package org.runestar.cs2.dfa

import org.runestar.cs2.SCRIPT_ARGS
import org.runestar.cs2.bin.Type
import org.runestar.cs2.ir.Function
import org.runestar.cs2.ir.FunctionSet
import org.runestar.cs2.ir.Prototype
import org.runestar.cs2.ir.Typing
import org.runestar.cs2.ir.assign
import org.runestar.cs2.ir.identifier
import org.runestar.cs2.ir.replace

object ReorderArgs : Phase.Individual() {

    override fun transform(f: Function, fs: FunctionSet) {
        val oldStackTypes = f.arguments.map { it.stackType }
        val typing = fs.typings.args(f.id, oldStackTypes)
        val manualArguments: List<Type>? = SCRIPT_ARGS.load(f.id)
        val newStackTypes = manualArguments?.map { it.stackType } ?: typing.map { it.stackType }
        if (manualArguments != null) {
            require(typing.size == manualArguments.size) { "argument override out of date for script: ${f.id}, ${typing.size} != ${manualArguments.size}" }
            val oldTyping = typing.toMutableList()
            val newTyping = newStackTypes.map { st -> oldTyping.removeAt(oldTyping.indexOfFirst { it.stackType == st }) }
            assign(manualArguments.map { fs.typings.of(Prototype(it)) }, newTyping)
        }
        if (oldStackTypes == newStackTypes) return
        val oldArgs = f.arguments.toMutableList()
        val newArgs = newStackTypes.map { st -> oldArgs.removeAt(oldArgs.indexOfFirst { it.stackType == st }) }
        f.arguments = newArgs
    }
}