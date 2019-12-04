package com.github.apottere.advent.nineteen

import com.github.apottere.advent.Day
import org.amshove.kluent.`should be equal to`
import kotlin.math.absoluteValue

class Day4: Day<Pair<Int, Int>>(4, {
    format { reader ->
        val parts = reader.readLine().split('-').also {
            it.size `should be equal to` 2
        }

        parts[0].toInt() to parts[1].toInt()
    }

    problem(1) {
        test("111110-111112" to 2)

        solution { (input) ->
            input.first.rangeTo(input.second)
                .filter { numberMatchesFormat(it) { group -> group.size >= 2 } }
                .count()
        }
    }

    problem(2) {
        test("111110-111112" to 0)

        solution { (input) ->
            input.first.rangeTo(input.second)
                .filter { numberMatchesFormat(it) { group -> group.size == 2 } }
                .count()
        }
    }
})

fun numberMatchesFormat(number: Int, matchDoubles: (group: List<Int>) -> Boolean): Boolean {
    if(number == 0) {
        return false
    }

    val digits = generateSequence(number.absoluteValue to null as Int?) { (currentNumber, _) ->
        if(currentNumber == 0) {
            return@generateSequence null
        }

        currentNumber / 10 to currentNumber % 10
    }
        .map { it.second }
        .filterNotNull()
        .toList()
        .asReversed()

    if(digits.size != 6) {
        return false
    }

    // Check for increasing digits
    if(!foldWithPrevious(digits, true) { success, previous, current -> success && previous <= current }) {
        return false
    }

    // Since digits are ever-increasing, all instances of the same number must be sequential
    if(digits.groupBy { it }.values.none(matchDoubles)) {
        return false
    }

    return true
}

fun <T, R> foldWithPrevious(iterable: Iterable<T>, initial: R, operation: (R, previous: T, current: T) -> R): R {
    return iterable.fold(initial to null as T?) { (result, previous), current ->
        if(previous == null) {
            return@fold result to current
        }

        operation(result, previous, current) to current
    }.first
}
