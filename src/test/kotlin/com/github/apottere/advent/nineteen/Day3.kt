package com.github.apottere.advent.nineteen

import com.github.apottere.advent.Day
import kotlin.math.abs

class Day3: Day<Pair<Sequence<String>, Sequence<String>>>(3, {
    format { reader ->
        val lines = reader.lineSequence().filter { it.isNotBlank() }.toList()
        if(lines.size != 2) {
            throw IllegalArgumentException("Malformed input, expected 2 lines but got: ${lines.size}")
        }

        lines[0].splitToSequence(',') to lines[1].splitToSequence(',')
    }

    problem(1) {
        test("R75,D30,R83,U83,L12,D49,R71,U7,L72\nU62,R66,U55,R34,D71,R55,D58,R83" to 159)
        test("R98,U47,R26,D63,R33,U87,L62,D20,R33,U53,R51\nU98,R91,D20,R16,D67,R40,U7,R15,U6,R7" to 135)

        solution { (input) ->
            val line1 = traceWire(input.first).toSet()
            traceWire(input.second)
                .filter { line1.contains(it) }
                .map { abs(it.first) + abs(it.second) }
                .min()
                ?: throw IllegalStateException("No intersections found!")
        }
    }
})

fun traceWire(instructions: Sequence<String>): Sequence<Coordinate> {
    var current = Coordinate(0, 0)

    return instructions.flatMap { instruction ->
        val direction = instruction[0]
        val distance = instruction.substring(1).toInt()

        val transform: (Coordinate) -> Coordinate = when(direction) {
            'U' -> {{ Coordinate(it.first + 1, it.second) }}
            'D' -> {{ Coordinate(it.first - 1, it.second) }}
            'R' -> {{ Coordinate(it.first, it.second + 1) }}
            'L' -> {{ Coordinate(it.first, it.second - 1) }}
            else -> throw IllegalArgumentException("Invalid direction: $direction")
        }

        1.rangeTo(distance).asSequence().map {
            current = transform(current)
            current
        }
    }
}

typealias Coordinate = Pair<Int, Int>
