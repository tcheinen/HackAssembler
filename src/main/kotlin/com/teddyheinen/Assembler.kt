package com.teddyheinen

import java.io.File


fun main(args: Array<String>) {
    val toBeAssembled: MutableList<String> = mutableListOf()
    if (args.isNotEmpty()) {
        args.forEach { toBeAssembled.add(it) }
    } else {
        File("asm/").walkTopDown().filter { it.extension == "asm" }.forEach {
            toBeAssembled.add(it.absolutePath)
        }
    }

    toBeAssembled.forEach {
        val f = File(it)
        println("Assembling ${f.name}")
        val (generatedCode: List<String>, debugASM: List<String>) = assemble(f)
        val out = File("asm/${f.nameWithoutExtension}.hack")
        out.writeText(generatedCode.joinToString("\n"))
        compare("asm/${f.nameWithoutExtension}.hack", debugASM)
    }

}


/**
 * Compares a compiled hack file to the reference file
 * @param filename path to the compiled hack file
 * @param debugASM cleaned ASM, used to be able to print out the ASM around an error
 */
fun compare(filename: String, debugASM: List<String>) {
    val f = File(filename)
    println("Comparing ${f.nameWithoutExtension} with reference")
    val generatedCode = f.readLines()
    val reference = File("asm/${f.nameWithoutExtension}_reference.hack").readLines()

    if (reference.size != generatedCode.size)
        throw Exception("Length of reference and generated code not the same for ${f.nameWithoutExtension}")
    reference.forEachIndexed { index, _ ->
        if (reference[index] != generatedCode[index]) {
            val debugLines: String = (-3..3).joinToString("\n") { debugASM[index + it] }
            val errorText =
                "Reference and generated code different at line $index\nLine in ASM: ${debugASM[index]} \n$debugLines"
            throw Exception(errorText)
        }
    }
}

/**
 * Strips whitespace or comments from a list of assembly instructions
 * @param f Lines in a Hack ASM file
 * @return Hack ASM lines with any whitespace or comments removed
 */
fun clean(f: List<String>): List<String> {
    val commentRegex = "(//.*\$)|(\\s)".toRegex()

    return f.map { it.replace(commentRegex, "") }.filter { it != "" }

}

/**
 * Reads a file object and returns assembled code
 * @param f File object pointing to the ASM file to be assembled
 * @return A pair containing the binary output and the cleaned ASM
 */
fun assemble(f: File): Pair<List<String>, List<String>> {
    val code: List<String> = clean(f.readLines())
    val (codeProcessedLabels, labels) = labelize(code)
    val codeProcessedInstructions = processInstructions(codeProcessedLabels, labels)
    return Pair(codeProcessedInstructions, code)
}


/**
 * Reads in a list of assembly instruction and returns a copy without labels and a label mapping\n
 * corresponds to phase 1 of the provided assembler process
 * @param code A list containing the string value of each line of a Hack ASM file
 * @return A list of strings with any label instructions and a label <-> line number mapping
 */
fun labelize(code: List<String>): Pair<List<String>, Map<String, Int>> {

    /**
     * Should be a fairly straightforward function
     * It will iterate over each line and strip any label instructions after storing the line number
     */

    val labelRegex = "\\(.*\\)".toRegex()
    val labels: MutableMap<String, Int> = mutableMapOf()

    val output: MutableList<String> = mutableListOf()
    var counter = 0
    for (i in code) {
        val match = labelRegex.find(i)
        if (match != null) {
            labels[match.groupValues.first().slice(1..i.length - 2)] = counter
            continue
        }
        output.add(i)
        counter++
    }
    return Pair(output, labels)
}

/**
 * Reads in a list of assembly instruction and returns a list of binary instructions\n
 * corresponds to phase 2 of the provided assembler process
 * @param code List of ASM instructions
 * @param labels Label <-> Line number mapping
 * @return Compiled Hack binary in a list of strings
 */
fun processInstructions(code: List<String>, labels: Map<String, Int>): List<String> {

    /**
     * I actually rather like how this turned out
     * First, any instructions which starts with @ is an A instruction.  The remainder of the instruction is passed through
     * the symbol function and then converted into base 2
     * If it isn't an A instruction we can assume it is a C instruction.  First, I split the instruction into three segments
     * Each segment is considered separately.  dest and jmp are both easily done programmatically.  I check for a couple
     * conditions and set bits as required. The comp instruction is a bit more of a hassle, I ended up just writing a function
     * to map comp instructions to the appropriate bitstrings
     */

    val output: MutableList<String> = mutableListOf()
    val variables: MutableMap<String, Int> = mutableMapOf()

    for (i in code) {
        val instruction = if (i[0] == "@".single()) {
            // A Instruction
            val num = symbol(i.slice(1 until i.length), labels, variables).toInt().toString(2)
            "0${num.padStart(15, "0".single())}"
        } else {
            // C Instruction
            val instr = i.toLowerCase()

            val splitDest = instr.split("=")
            val splitJmp = instr.split(";")

            val dest = if (splitDest.size > 1) splitDest.first() else ""
            val comp = instr.split("=").last().split(";").first()
            val jmp = if (splitJmp.size > 1) splitJmp.last() else ""

            val destA = dest.contains("a")
            val destD = dest.contains("d")
            val destM = dest.contains("m")
            val destBinary = listOf(destA, destD, destM).joinToString("") { if (it) "1" else "0" }

            val compBinary = comp(comp)

            val jmpEq = jmp == "jeq" || jmp == "jge" || jmp == "jle" || jmp == "jmp"
            val jmpLt = jmp == "jlt" || jmp == "jne" || jmp == "jle" || jmp == "jmp"
            val jmpGt = jmp == "jgt" || jmp == "jge" || jmp == "jne" || jmp == "jmp"
            val jmpBinary = listOf(jmpLt, jmpEq, jmpGt).joinToString("") { if (it) "1" else "0" }
            "111$compBinary$destBinary$jmpBinary"
        }
        output.add(instruction)
    }
    return output
}


/**
 * Converts symbols into their integer representation
 * @param sym The symbol to be converted
 * @param labels A mapping of label <-> line number
 * @param variables A mapping of variables to their memory locations
 * @return The integer which the symbol corresponds to
 */
fun symbol(sym: String, labels: Map<String, Int>, variables: MutableMap<String, Int>): String {

    /**
     * this is kinda a mess but i'm not sure of a better way to do it.  I'll outline the workflow below
     * 1. Any symbol that is a valid integer will be returned without any modification
     * 2. Any symbol in which the first character is an R and the remaining characters map to an integer between 0 and 16
     * will return that number
     * 3. Any symbol which corresponds to a constant will return the appropriate number for that constant
     * 4. Any remaining symbol is either a label or a variable.  If a label can be found it will return the stored line number
     * otherwise it will return the stored memory location of the variable or assign a new one if it is the first occurence
     */

    if ((sym.toIntOrNull() != null)) return sym.toIntOrNull().toString()

    if (sym[0] == "R".single()) {
        val remainder = sym.slice(1 until sym.length)
        if (remainder.toIntOrNull() != null && remainder.toInt() in 0..17) {
            return sym.slice(1 until sym.length)
        }
    }

    val constant = when (sym) {
        "SP" -> "0"
        "LCL" -> "1"
        "ARG" -> "2"
        "THIS" -> "3"
        "THAT" -> "4"
        "SCREEN" -> "16384"
        "KBD" -> "24576"
        else -> null
    }
    if (constant != null) return constant

    return if (labels[sym] != null) {
        labels.getValue(sym).toString()
    } else {
        if (sym !in variables) variables[sym] = 16 + variables.size
        "${variables[sym]}"
    }

}


/**
 * Converts comp instructions into their binary representation
 * @param comp Comp instruction such as D+1 or M-D
 * @return Binary representation of the instruction
 */
fun comp(comp: String): String {

    // first char is A bit, determines a/m

    return when (comp) {
        "0" -> "0101010"
        "1" -> "0111111"
        "-1" -> "0111010"
        "d" -> "0001100"
        "a" -> "0110000"
        "m" -> "1110000"
        "!d" -> "0001101"
        "!a" -> "0110001"
        "!m" -> "1110001"
        "-d" -> "0001111"
        "-a" -> "0110011"
        "-m" -> "1110011"
        "d+1" -> "0011111"
        "a+1" -> "0110111"
        "m+1" -> "1110111"
        "d-1" -> "0001110"
        "a-1" -> "0110010"
        "m-1" -> "1110010"
        "d+a" -> "0000010"
        "d+m" -> "1000010"
        "d-a" -> "0010011"
        "d-m" -> "1010011"
        "a-d" -> "0000111"
        "m-d" -> "1000111"
        "d&a" -> "0000000"
        "d&m" -> "1000000"
        "d|a" -> "0010101"
        "d|m" -> "1010101"
        else -> throw Exception("$comp is not a valid comp instruction")
    }
}