package org.runestar.cs2.cfa

import org.runestar.cs2.ir.Expression
import org.runestar.cs2.ir.Function
import org.runestar.cs2.ir.Instruction
import org.runestar.cs2.util.isSuccessorAcyclic

fun reconstruct(f: Function): Construct {
    val fg = FlowGraph(f)
    val root = Construct.Seq()
    reconstructBlock(fg, root, fg.graph.head, fg.graph.head)
    return root
}

private fun reconstructBlock(
        flow: FlowGraph,
        prev: Construct,
        dominator: BasicBlock,
        block: BasicBlock
): BasicBlock? {
    if (dominator != block && !flow.dtree.isSuccessorAcyclic(dominator, block)) return block
    val seq = if (prev is Construct.Seq) {
        prev
    } else {
        val s = Construct.Seq()
        prev.next = s
        s
    }
    for (insn in block) {
        if (insn is Instruction.Label) continue
        if (insn == block.tail) continue
        seq.instructions.add(insn)
    }
    when (val tail = block.tail) {
        is Instruction.Return -> {
            seq.instructions.add(tail)
        }
        is Instruction.Goto -> {
            return reconstructBlock(flow, seq, dominator, flow.blocks.block(tail.label))
        }
        is Instruction.Switch -> {
            var after: BasicBlock? = null
            val cases = mutableListOf<Construct.SwitchCase>()
            val casesBlocks = mutableMapOf<Construct.SwitchCase, Int>()
            val switch = Construct.Switch(tail.expression, cases)
            seq.next = switch
            val next = flow.blocks.next(block)
            for (v in tail.cases.values.toSet()) {
                val keys = LinkedHashSet<Int>()
                for (e in tail.cases) {
                    if (e.value == v) keys.add(e.key)
                }
                val nxt = Construct.Seq()
                val case = Construct.SwitchCase(keys, nxt)
                cases.add(case)
                val dst = flow.blocks.block(v)
                casesBlocks[case] = dst.index

                val otherCaseBypassesEdge = tail.cases.values.any { it != v && findDescendants(flow, flow.blocks.block(it), block).contains(dst) }
                val defaultCaseBypassesEdge = findDescendants(flow, next, block).contains(dst)
                after = (if (!otherCaseBypassesEdge && !defaultCaseBypassesEdge) {
                    reconstructBlock(flow, nxt, dst, dst)
                } else {
                    // the fail edge doesn't dominate the fail block (there's a second path through another case or the default), so the case is empty
                    dst
                }) ?: after
            }
            val default = Construct.Seq()
            after = reconstructBlock(flow, default, next, next) ?: after
            if (default.instructions.isNotEmpty() || default.next != null) {
                val branch = next.head
                // This check is necessary because the branch instruction is optimised
                // out when default is defined at the top
                val index = if (branch is Instruction.Goto) flow.blocks.block(branch.label).index else next.index
                val case = Construct.SwitchCase(null, default)
                cases.add(case)
                casesBlocks[case] = index
            }
            cases.sortBy { casesBlocks[it] }
            if (after != null) {
                return reconstructBlock(flow, switch, block, after)
            }
        }
        is Instruction.Branch -> {
            val pass = flow.blocks.block(tail.pass)
            val fail = flow.blocks.next(block)
            val preds = flow.graph.immediatePredecessors(block)
            if (preds.any { it.index > block.index }) {
                val w = Construct.While(tail.expression as Expression.Operation)
                seq.next = w
                w.body = Construct.Seq()
                reconstructBlock(flow, w.body, pass, pass)
                return reconstructBlock(flow, w, fail, fail)
            } else {
                val iff = Construct.If()
                seq.next = iff
                val branch = Construct.Branch(tail.expression as Expression.Operation, Construct.Seq())
                iff.branches.add(branch)
                val afterIf = if (!findDescendants(flow, fail, block).contains(pass)) {
                    reconstructBlock(flow, branch.body, pass, pass)
                } else {
                    // the pass edge doesn't dominate the pass block (there's a second path through
                    // the fail block), so the pass branch is empty
                    pass
                }
                val elze = Construct.Seq()
                iff.elze = elze
                val afterElze = if (!findDescendants(flow, pass, block).contains(fail)) {
                    reconstructBlock(flow, elze, fail, fail)
                } else {
                    // the fail edge doesn't dominate the fail block (there's a second path through
                    // the pass block), so the pass branch is empty
                    fail
                }
                if (elze.instructions.isEmpty() && elze.next == null) {
                    iff.elze = null
                } else if (elze.instructions.isEmpty() && elze.next is Construct.If && elze.next!!.next == null) {
                    val if2 = elze.next as Construct.If
                    iff.branches.addAll(if2.branches)
                    iff.elze = if2.elze
                }

                if (afterIf == null) {
                    iff.next = iff.elze
                    iff.elze = null
                    if (afterElze != null) {
                        return reconstructBlock(flow, iff.next ?: iff, block, afterElze)
                    }
                } else {
                    return reconstructBlock(flow, iff, block, afterIf)
                }
            }
        }
        is Instruction.Assignment -> {
            seq.instructions.add(tail)
            return reconstructBlock(flow, seq, dominator, flow.blocks.next(block))
        }
        is Instruction.Label -> {
            return reconstructBlock(flow, seq, dominator, flow.blocks.next(block))
        }
    }
    return null
}

private fun findDescendants(flow: FlowGraph, start: BasicBlock, stop: BasicBlock): HashSet<BasicBlock> {
    val result = HashSet<BasicBlock>()
    val queue = ArrayDeque<BasicBlock>()
    queue.add(start)

    while (!queue.isEmpty()) {
        val block = queue.removeFirst()

        if (block != stop && result.add(block)) {
            queue.addAll(flow.graph.immediateSuccessors(block))
        }
    }

    return result
}