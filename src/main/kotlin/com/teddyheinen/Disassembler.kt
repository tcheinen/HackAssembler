package com.teddyheinen

fun main(args: Array<String>) {
    val n = readLine()!!.toInt()
    val code = mutableListOf<String>()
    for(i in 0 until n) {
        code.add(readLine()!!)
    }
    val out: List<String> = code.map { processLine(it) }
    out.forEach { println(it) }
}

fun processLine(line: String): String {
    val instr = when(line[0]) {
        '0' -> aInstr(line)
        '1' -> cInstr(line)
        else -> ""
    }

    return instr
}

fun aInstr(line: String): String {
    return "@${Integer.parseInt(line.slice(1..15), 2)}"
}

fun cInstr(line: String): String {
    val comp = disasComp(line.slice(3..9))
    val dst = disasDest(line.slice(10..12))
    val jmp = disasJmp(line.slice(13..15))
    return "$dst${if(dst.length != 0) "=" else ""}$comp${if(jmp.length != 0) ";" else ""}$jmp"
}

fun disasDest(dest: String): String {
    val d1 = if(dest[0] == '1') "A" else ""
    val d2 = if(dest[1] == '1') "D" else ""
    val d3 = if(dest[2] == '1') "M" else ""
    return "$d1$d3$d2"
}

fun disasJmp(jmp: String): String {
    return when (jmp) {
        "000" -> ""
        "001" -> "JGT"
        "010" -> "JEQ"
        "011" -> "JGE"
        "100" -> "JLT"
        "101" -> "JNE"
        "110" -> "JLE"
        "111" -> "JMP"
        else -> throw Exception("$jmp is not a valid jmp instruction")
    }
}

fun disasComp(comp: String): String {
    return when (comp) {
        "0101010" -> "0"
        "0111111" -> "1"
        "0111010" -> "-1"
        "0001100" -> "D"
        "0110000" -> "A"
        "1110000" -> "M"
        "0001101" -> "!D"
        "0110001" -> "!A"
        "1110001" -> "!M"
        "0001111" -> "-D"
        "0110011" -> "-A"
        "1110011" -> "-M"
        "0011111" -> "D+1"
        "0110111" -> "A+1"
        "1110111" -> "M+1"
        "0001110" -> "D-1"
        "0110010" -> "A-1"
        "1110010" -> "M-1"
        "0000010" -> "D+A"
        "1000010" -> "D+M"
        "0010011" -> "D-A"
        "1010011" -> "D-M"
        "0000111" -> "A-D"
        "1000111" -> "M-D"
        "0000000" -> "D&A"
        "1000000" -> "D&M"
        "0010101" -> "D|D"
        "1010101" -> "D|M"
        else -> throw Exception("$comp is not a valid comp instruction")
    }
}