package org.runestar.cs2.dfa

import org.runestar.cs2.bin.*
import org.runestar.cs2.bin.StackType
import org.runestar.cs2.ir.Element
import org.runestar.cs2.ir.Expression
import org.runestar.cs2.ir.Function
import org.runestar.cs2.ir.FunctionSet
import org.runestar.cs2.ir.Instruction
import org.runestar.cs2.ir.Variable
import org.runestar.cs2.ir.asList
import org.runestar.cs2.ir.assign

object FindArrayArgs : Phase.Individual() {

    override fun transform(f: Function, fs: FunctionSet) {
        if (f.arguments.none { it.stackType == StackType.INT }) return
        val definedArrays =  mutableSetOf<Int>()
        for (insn in f.instructions) {
            if (insn !is Instruction.Assignment) continue
            val expr = insn.expression
            if (expr !is Expression.Operation) continue
            val exprArgs = expr.arguments.asList

            when (expr.opcode) {
                DEFINE_ARRAY -> {
                    definedArrays += (exprArgs[0] as Element.Access).variable.id
                }

                POP_ARRAY_INT, PUSH_ARRAY_INT -> {
                    val arrayId = (exprArgs[0] as Element.Access).variable.id
                    if (arrayId in definedArrays) continue

                    definedArrays += arrayId

                    // Right now, we only handle the first array argument, since they do not use more
                    // than one array, that is not problematic, we could assume argument index should match
                    // array id to support multiple arrays, but we have no proof of such thing.

                    val argIndex = f.arguments.indexOfFirst { it.stackType == StackType.INT }
                    val oldVar = f.arguments[argIndex]
                    val newVar = Variable.array(f.id, 0)
                    val newVars = f.arguments.toMutableList()

                    newVars[argIndex] = newVar
                    f.arguments = newVars

                    val t = fs.typings.of(newVar)
                    val ts = fs.typings.args(f.id, f.arguments.map { it.stackType }).toMutableList()
                    ts[argIndex] = t

                    fs.typings.remove(oldVar)
                    fs.typings.args[f.id] = ts
                    transformCalls(f.id, fs, argIndex)
                }
            }
        }
    }

    private fun transformCalls(scriptId: Int, fs: FunctionSet, argIndex: Int) {
        for (f in fs.functions.values) {
            for (insn in f.instructions) {
                if (insn !is Instruction.Assignment) continue
                val e = insn.expression
                if (e !is Expression.Proc) continue
                if (e.scriptId != scriptId) continue

                // todo
                val oldArgs = e.arguments.asList
                val oldArg = oldArgs[argIndex] as Element.Access
                val newArgs = oldArgs.toMutableList()
                val newArg = Element.Pointer(Variable.array(f.id, 0))
                newArgs[argIndex] = newArg
                e.arguments = Expression(newArgs)
                if (f.id != scriptId) {
                    assign(fs.typings.of(newArg), fs.typings.args.getValue(scriptId)[argIndex])
                }
            }
        }
    }
}