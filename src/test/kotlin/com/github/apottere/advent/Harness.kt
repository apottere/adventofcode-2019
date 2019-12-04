package com.github.apottere.advent

import org.amshove.kluent.shouldEqual
import org.junit.platform.commons.annotation.Testable
import org.junit.platform.commons.support.ReflectionSupport
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.support.descriptor.*
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine
import org.junit.platform.engine.support.hierarchical.Node
import org.junit.platform.engine.support.hierarchical.ThrowableCollector
import java.io.BufferedReader
import java.io.File

@DslMarker
annotation class HarnessDsl

@HarnessDsl
class DayDsl<T>(private val context: Context<T>) {

    fun format(formatter: Formatter<T>) {
        context.formatter = formatter
    }

    fun problem(problem: Int, body: ProblemDsl<T>.() -> Unit) {
        val (tests, solution) = ProblemDsl<T>().also(body)
        context.problems.add(
            Problem(
                problem,
                tests,
                solution ?: throw IllegalArgumentException("`solution` must be provided in problem DSL!")
            ) to callerLocation()
        )
    }
}

@HarnessDsl
data class ProblemDsl<T>(val tests: MutableList<TestWithLocation> = mutableListOf(), var solution: SolutionWithLocation<T>? = null) {
    fun test(test: Test) {
        tests.add(test to callerLocation())
    }

    fun solution(solution: Solution<T>) {
        this.solution = solution to callerLocation()
    }
}

typealias Formatter<T> = (reader: BufferedReader) -> T
typealias Test = Pair<String, Any>
typealias TestWithLocation = Pair<Test, TestSource>
data class Input<T>(val input: T, val testing: Boolean = false)
typealias Solution<T> = (input: Input<T>) -> Any
typealias SolutionWithLocation<T> = Pair<Solution<T>, TestSource>
typealias Problem<T> = Triple<Int, List<TestWithLocation>, SolutionWithLocation<T>>
typealias ProblemWithLocation<T> = Pair<Problem<T>, TestSource>
data class Context<T>(
    var formatter: Formatter<T>? = null,
    val problems: MutableList<ProblemWithLocation<T>> = mutableListOf()
)

fun callerLocation(): TestSource {
    val location = RuntimeException().stackTrace[2]
    return FileSource.from(File(location.className.replace("\\.[^.]*$".toRegex(), "").replace(".", "/"), location.fileName!!), FilePosition.from(location.lineNumber))
}

@Testable
abstract class Day<T>(private val day: Int, configure: DayDsl<T>.() -> Unit) {
    private val context = Context<T>()

    init {
        DayDsl(context).configure()
    }

    fun getTestDescriptors(uniqueId: UniqueId): AdventContainerDescriptor {
        val (formatter, problems) = context

        if(formatter == null) {
            throw IllegalArgumentException("`formatter` must be specified in day DSL!")
        }

        if(problems.isNullOrEmpty()) {
            throw IllegalArgumentException("At least one `problem` must be specified in day DSL!")
        }

        val dayId = uniqueId.append("day", day.toString())
        val problemDescriptors = problems.map { (problem, problemLocation) ->
            val (number, tests, solution) = problem
            val (solutionBody, solutionLocation) = solution
            val problemId = dayId.append("problem", number.toString())

            val testDescriptors = tests.mapIndexed { index, (test, testLocation) ->
                val (input, expected) = test
                val prettyIndex = (index + 1).toString()
                AdventTestDescriptor(problemId.append("test", prettyIndex), "Test #${prettyIndex}", testLocation) {
                    println("Input: $input")
                    println("Expected: $expected")

                    val result = input.reader().buffered().use {
                        solutionBody(Input(formatter(it), true))
                    }
                    println("Result: $result")

                    result shouldEqual expected
                }
            }

            val solutionDescriptor = AdventTestDescriptor(problemId.append("solution", "0"), "Solution", solutionLocation) {
                val inputFile = "input/day${day}.txt"
                val result = (object {}.javaClass.classLoader.getResourceAsStream(inputFile)
                    ?: throw IllegalArgumentException("Input file not found: $inputFile")).bufferedReader().use {
                    solutionBody(Input(formatter(it)))
                }
                println("Answer: $result")
            }

            AdventContainerDescriptor(problemId, "Problem #${number}", problemLocation, testDescriptors + listOf(solutionDescriptor))
        }

        return AdventContainerDescriptor(dayId, "Day #${day}", ClassSource.from(this::class.java), problemDescriptors)
    }
}

class AdventTestEngine: HierarchicalTestEngine<AdventEngineContext>() {
    override fun getId() = "advent"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val selectors = discoveryRequest.getSelectorsByType(DiscoverySelector::class.java)
        val selections = selectors.flatMap { selector ->
            when(selector) {
                is ClassSelector -> {
                    when {
                        isDayClass(selector.javaClass) -> listOf(Selection(selector.javaClass))
                        else -> listOf()
                    }
                }
                is ClasspathRootSelector -> {
                    val classes = ReflectionSupport.findAllClassesInClasspathRoot(
                        selector.classpathRoot,
                        { isDayClass(it) },
                        { true }
                    )

                    classes.map { Selection(it) }
                }

                else -> throw IllegalArgumentException("Unknown selector: ${selector.javaClass.name}")
            }
        }

        val tests = selections.associate {
            val instance = ReflectionSupport.newInstance(it.testClass) as Day<*>
            it.testClass to TestSuite(instance, instance.getTestDescriptors(uniqueId))
        }

        return AdventRootContainerDescriptor(uniqueId, tests.values.map { it.tests })
    }

    override fun createExecutionContext(request: ExecutionRequest): AdventEngineContext {
        return AdventEngineContext()
    }
}

class AdventRootContainerDescriptor(uniqueId: UniqueId, children: List<TestDescriptor>): EngineDescriptor(uniqueId, "All Days"), Node<AdventEngineContext> {
    init {
        children.forEach { addChild(it) }
    }

    override fun getExecutionMode(): Node.ExecutionMode = Node.ExecutionMode.CONCURRENT
}

class AdventContainerDescriptor(uniqueId: UniqueId, displayName: String, source: TestSource?, children: List<TestDescriptor>): AbstractTestDescriptor(uniqueId, displayName, source), Node<AdventEngineContext> {
    init {
        children.forEach { addChild(it) }
    }
    override fun getType() = TestDescriptor.Type.CONTAINER
}

class AdventTestDescriptor(uniqueId: UniqueId, displayName: String, source: TestSource?, private val testBody: () -> Unit): AbstractTestDescriptor(uniqueId, displayName, source), Node<AdventEngineContext> {
    override fun getType() = TestDescriptor.Type.TEST
    override fun execute(context: AdventEngineContext, dynamicTestExecutor: Node.DynamicTestExecutor): AdventEngineContext {
        context.throwableCollector.execute {
            this.testBody()
        }
        return context
    }
}

class AdventEngineContext: EngineExecutionContext {
    val throwableCollector = ThrowableCollector { false }
}

fun isDayClass(testClass: Class<*>) = testClass != Day::class.java && Day::class.java.isAssignableFrom(testClass)

data class Selection(val testClass: Class<*>, val uniqueId: String? = null)
data class TestSuite(val instance: Day<*>, val tests: AdventContainerDescriptor)

fun tmpLog(msg: String) {
    File("/tmp/test-output.txt").appendText(msg + "\n")
}
