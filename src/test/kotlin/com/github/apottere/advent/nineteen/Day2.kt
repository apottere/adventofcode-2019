package com.github.apottere.advent.nineteen

import com.github.apottere.advent.Day


class Day2: Day<List<Int>>(2, {
    format { reader ->
        reader.lineSequence()
            .flatMap { it.splitToSequence(',') }
            .dropWhile { it.isBlank() }
            .map { it.toInt() }
            .toList()
    }

    problem(1) {
        test("1,9,10,3,2,3,11,0,99,30,40,50" to "3500,9,10,70,2,3,11,0,99,30,40,50")
        test("1,0,0,0,99" to "2,0,0,0,99")
        test("2,3,0,3,99" to "2,3,0,6,99")
        test("2,4,4,5,99,0" to "2,4,4,5,99,9801")
        test("1,1,1,4,99,5,6,0,99" to "30,1,1,4,2,5,6,0,99")

        solution { (input, testing) ->
            val register = ArrayList(input)

            when {
                testing -> intcodeCompute(register) { it.joinToString(",")}
                else -> {
                    register[1] = 12
                    register[2] = 2
                    intcodeCompute(register) { it[0] }
                }
            }
        }
    }

    problem(2) {
        solution { (input) ->
            val (noun, verb) = 0.rangeTo(99).asSequence()
                .flatMap { noun -> 0.rangeTo(99).asSequence().map { verb -> noun to verb } }
                .find { (noun, verb) ->
                    val register = ArrayList(input)
                    register[1] = noun
                    register[2] = verb
                    intcodeCompute(register) { it[0] } == 19690720
                }
                ?: throw IllegalStateException("No result found")

            (noun * 100) + verb
        }
    }
})

val intcodeOperations = mapOf<Int, (Int, Int) -> Int>(
    1 to Int::plus,
    2 to Int::times
)

fun intcodeCompute(register: MutableList<Int>, result: (register: List<Int>) -> Any): Any {
    var cursor = 0
    loop@ while(true) {
        when(val opcode = register[cursor]) {
            99 -> break@loop
            1, 2 -> {
                val (arg1Index, arg2Index, outputIndex) = register.subList(cursor + 1, cursor + 4)
                val operation = intcodeOperations[opcode] ?: throw IllegalStateException("Missing operator for opcode: $opcode")
                register[outputIndex] = operation(register[arg1Index], register[arg2Index])
            }
            else -> throw IllegalArgumentException("Invalid opcode at position ${cursor}: $opcode")
        }

        cursor += 4
    }

    return result(register)
}

