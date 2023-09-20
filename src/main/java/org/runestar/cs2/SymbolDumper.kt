package org.runestar.cs2

import org.runestar.cs2.bin.Script
import org.runestar.cs2.bin.Type
import org.runestar.cs2.cg.StrictGenerator
import org.runestar.cs2.cg.TYPE_SYMBOLS
import org.runestar.cs2.ir.FunctionSet
import org.runestar.cs2.ir.Variable
import org.runestar.cs2.ir.type
import org.runestar.cs2.util.Loader
import org.runestar.cs2.util.caching
import org.runestar.cs2.util.list
import org.runestar.cs2.util.withIds
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

fun main() {
    val loadDir = Path.of("input")
    val saveDir = Path.of("symbols")
    Files.createDirectories(saveDir)

    val generator = StrictGenerator { _, _, _ -> }
    val scriptLoader = Loader { Script(Files.readAllBytes(loadDir.resolve(it.toString()))) }.caching()
    val scriptIds = loadDir.list().mapTo(TreeSet()) { it.toInt() }

    val fs = decompile(scriptLoader.withIds(scriptIds), generator)

    dumpVars(saveDir, fs)
    dumpAll(saveDir)
}

/**
 * Uses typing's from [fs] to dump all used game variables with their inferred types.
 */
private fun dumpVars(saveDir: Path, fs: FunctionSet) {
    val typeToName = mapOf(
        Variable.varp::class to "varp",
        Variable.varbit::class to "varbit",
        Variable.varcint::class to "varcint",
        Variable.varcstring::class to "varcstring",
        Variable.varclan::class to "varclan",
        Variable.varclansetting::class to "varclansetting",
    )
    val typeToSymbolPrefix = mapOf(
        Variable.varp::class to "var",
    )
    val typeToFileName = mapOf(
        Variable.varcint::class to "varc",
        Variable.varcstring::class to "varc",
    )

    val variableByType = fs.typings.variables.entries.asSequence()
        .sortedBy { it.key.id }
        .filter { it.key is Variable.Global }
        .groupBy { if (it.key::class == Variable.varcstring::class) Variable.varcint::class else it.key::class }

    for ((klazz, variables) in variableByType) {
        val fileName = typeToFileName[klazz] ?: typeToName[klazz]
        val saveFile = saveDir.resolve("$fileName.sym")
        val writer = Files.newBufferedWriter(saveFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        writer.use {
            for ((variable, typing) in variables) {
                val symbolPrefix = typeToSymbolPrefix[variable::class] ?: typeToName[variable::class]
                if (variable !is Variable.varbit) {
                    it.write("${variable.id}\t${symbolPrefix}${variable.id}\t${typing.type}\n")
                } else {
                    it.write("${variable.id}\t${symbolPrefix}${variable.id}\n")
                }
            }
        }
    }
}

/**
 * Dumps all references of different types.
 */
private fun dumpAll(saveDir: Path) {
    val skippedTypes = setOf(
        Type.INT, Type.BOOLEAN, Type.CHAR, Type.COORD, Type.NPC_UID, Type.TYPE, Type.ENTITYOVERLAY
    )
    val typeToName = mapOf(
        Type.NAMEDOBJ to "obj",
    )

    // merge objs into namedobjs
    mergeObjs()

    for ((type, symbols) in TYPE_SYMBOLS) {
        if (type in skippedTypes) {
            continue
        }
        dumpSymbolType(typeToName, type, saveDir, symbols)
    }
}

private fun dumpSymbolType(typeToName: Map<Type, String>, type: Type, saveDir: Path, symbols: TreeMap<Int, String>) {
    val saveFileName = typeToName[type] ?: type.literal
    val saveFile = saveDir.resolve("$saveFileName.sym")
    val writer = Files.newBufferedWriter(saveFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    writer.use {
        for ((id, _name) in symbols) {
            if (id == -1) continue

            // lookup return type of specific types
            val types = when (type) {
                Type.DBCOLUMN -> DBTABLE_TYPES.load(id) ?: error("null dbcolumn type")
                Type.PARAM -> when (val t = PARAM_TYPES.load(id)) {
                    null -> error("null param type")
                    else -> arrayOf(t)
                }

                else -> emptyArray()
            }.joinToString(",") { proto -> proto.type.literal }

            val name = if (type == Type.GRAPHIC) {
                // trim the quotes from sprite names since they're not necessary
                _name.trim('"')
            } else {
                _name
            }
            if (types.isEmpty()) {
                it.write("$id\t$name\n")
            } else {
                it.write("$id\t$name\t${types}\n")
            }
        }
    }
}

private fun mergeObjs() {
    val objs = TYPE_SYMBOLS[Type.OBJ]
    if (objs != null) {
        TYPE_SYMBOLS[Type.NAMEDOBJ]?.putAll(objs)
        TYPE_SYMBOLS.remove(Type.OBJ)
    }
}