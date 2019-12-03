package com.github.apottere.advent.nineteen

import com.github.apottere.advent.Day
import kotlin.math.floor

class Day1: Day<Sequence<Long>>(1, {
    format {
        it.lineSequence().map { line -> line.toLong() }
    }

    problem(1) {
        test("12" to 2L)
        test("14" to 2L)
        test("1969" to 654L)
        test("100756" to 33583L)

        solution {
            it.input.map(::calculateFuel).sum()
        }
    }

    problem(2) {
        test("14" to 2L)
        test("1969" to 966L)
        test("100756" to 50346L)

        solution { (input) ->
            input
                .map { mass ->
                    generateSequence(mass) { currentMass -> calculateFuel(currentMass).takeIf { it > 0 } }.drop(1).sum()
                }
                .sum()
        }
    }
})

fun calculateFuel(mass: Long) = floor(mass.div(3.0)).toLong().minus(2)
