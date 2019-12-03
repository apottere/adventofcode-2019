package com.github.apottere.advent

import org.amshove.kluent.shouldEqual
import org.junit.jupiter.engine.config.CachingJupiterConfiguration
import org.junit.jupiter.engine.config.DefaultJupiterConfiguration
import org.junit.jupiter.engine.config.JupiterConfiguration
import org.junit.jupiter.engine.execution.JupiterEngineExecutionContext
import org.junit.platform.commons.annotation.Testable
import org.junit.platform.commons.support.ReflectionSupport
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.PackageSource
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine
import org.junit.platform.engine.support.hierarchical.Node
import java.io.BufferedReader

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

fun callerLocation(): ClassSource {
    val location = RuntimeException().stackTrace[2]
    val rootClass = findRootClass(ReflectionSupport.tryToLoadClass(location.className).get())
    return ClassSource.from(rootClass.name, FilePosition.from(location.lineNumber))
}

fun findRootClass(subClass: Class<*>): Class<*> = when(val enclosingClass = subClass.enclosingClass) {
    null -> subClass
    else -> findRootClass(enclosingClass)
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

class AdventTestEngine: HierarchicalTestEngine<JupiterEngineExecutionContext>() {
    override fun getId() = "advent"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val configuration = CachingJupiterConfiguration(DefaultJupiterConfiguration(discoveryRequest.configurationParameters))
        val selectors = discoveryRequest.getSelectorsByType(DiscoverySelector::class.java)
        val selections = selectors.flatMap { selector ->
            when(selector) {
                is ClassSelector -> {
                    listOf(Selection(selector.javaClass))
                }
                is ClasspathRootSelector -> {
                    val classes = ReflectionSupport.findAllClassesInClasspathRoot(
                        selector.classpathRoot,
                        { it != Day::class.java && Day::class.java.isAssignableFrom(it) },
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

        return AdventRootContainerDescriptor(uniqueId, tests.values.map { it.tests }, configuration)
    }

    override fun createExecutionContext(request: ExecutionRequest): JupiterEngineExecutionContext {
        return JupiterEngineExecutionContext(
            request.engineExecutionListener,
            (request.rootTestDescriptor as AdventRootContainerDescriptor).configuration
        )
    }
}

class AdventRootContainerDescriptor(
    uniqueId: UniqueId,
    children: List<TestDescriptor>,
    val configuration: JupiterConfiguration
): AbstractTestDescriptor(uniqueId, "All Days", PackageSource.from(AdventRootContainerDescriptor::class.java.`package`)) {
    init {
        children.forEach { addChild(it) }
    }
    override fun getType() = TestDescriptor.Type.CONTAINER
}

class AdventContainerDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
    children: List<TestDescriptor>
): AbstractTestDescriptor(uniqueId, displayName, source) {
    init {
        children.forEach { addChild(it) }
    }
    override fun getType() = TestDescriptor.Type.CONTAINER
}

class AdventTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    source: TestSource?,
    private val testBody: () -> Unit
): AbstractTestDescriptor(uniqueId, displayName, source), Node<JupiterEngineExecutionContext> {

    override fun getType() = TestDescriptor.Type.TEST
    override fun execute(context: JupiterEngineExecutionContext, dynamicTestExecutor: Node.DynamicTestExecutor): JupiterEngineExecutionContext {
        this.testBody()
        return context
    }
}

data class Selection(val testClass: Class<*>, val uniqueId: String? = null) {
    init {
        if(!Day::class.java.isAssignableFrom(testClass)) {
            throw IllegalArgumentException("Test class does not implement Day: ${testClass.name}")
        }
    }
}

data class TestSuite(val instance: Day<*>, val tests: AdventContainerDescriptor)
