package com.palg.service


import com.google.gson.GsonBuilder
import com.intellij.compiler.CompilerMessageImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.palg.PalgIds
import com.palg.PalgUtils
import com.palg.error.ErrorCategoryNormalizer
import com.palg.error.RuntimeExceptionParser
import com.palg.model.ActivityData
import mu.KotlinLogging
import java.io.File


/**
 * project-level service for capturing compilation and runtime errors.
 *
 * We want compile-time errors to be structured. Intellij provides us an api
 * for that (CompilationStatusListener), but it requires project level service
 * we subscribe to compiler events via project.messageBus.
 *
 *  handles build/run lifecycle and
 * delegates console output to RuntimeExceptionParser for runtime error detection.
 */
@Service(Service.Level.PROJECT)
class PalgCompilerService(private val project: Project) : Disposable {

    private val logger = KotlinLogging.logger {}

    // converts ActivityData to json strings
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    // parser for extracting exceptions from console output
    private val runtimeParser = RuntimeExceptionParser()

    // buffer to accumulate console output during a run
    // parsed all at once when process terminates
    private val consoleBuffer = StringBuilder()

    // sets build id for compilation, cleared when compilation ends.
    // all errors during one compilation share this ID.
    // null when not running
    private var currentBuildId: String? = null
    private var currentRunId: String? = null


    private var currentBuildErrors: Int = 0
    private var currentBuildWarnings: Int = 0

    companion object {
        // cap buffer at 5mb to prevent oom if student writes infinite loop with println
        private const val MAX_BUFFER_SIZE = 5 * 1024 * 1024

        // https://platform.jetbrains.com/t/correct-way-of-retrieving-a-service/2787
        fun getInstance(project: Project): PalgCompilerService {
            return project.getService(PalgCompilerService::class.java)
        }
    }


    init {
        // ensure log directory exists
        val logDir = File(System.getProperty("user.home"), "PALG_logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        // Subscribe to compiler events
        installCompilerListener()
    }


    private fun emit(data: ActivityData) {
        logger.info { gson.toJson(data) }
    }


    // build lifecycle
    private fun ensureBuildStarted() {
        if (currentBuildId != null) return

        currentBuildId = PalgIds.newBuildId()
        currentBuildErrors = 0
        currentBuildWarnings = 0

        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "BuildStart",
                buildId = currentBuildId
            )
        )
    }

    // called when compilation finishes: collects compiler errors and warnings and logs each as a structured event

    private fun onCompilationFinished(
        aborted: Boolean,
        errors: Int,
        warnings: Int,
        ctx: CompileContext
    ) {
        ensureBuildStarted()

        // Process all errors
        for (msg in ctx.getMessages(CompilerMessageCategory.ERROR)) {
            emitCompileError(msg, "error")
            currentBuildErrors++
        }

        // Process all warnings
        for (msg in ctx.getMessages(CompilerMessageCategory.WARNING)) {
            emitCompileError(msg, "warning")
            currentBuildWarnings++
        }

        // Log build completion
        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "BuildEnd",
                buildId = currentBuildId,
                success = currentBuildErrors == 0,
                errorCount = currentBuildErrors,
                warningCount = currentBuildWarnings,
                message = if (aborted) {
                    "Build aborted ($currentBuildErrors errors, $currentBuildWarnings warnings)"
                } else {
                    "Build completed ($currentBuildErrors errors, $currentBuildWarnings warnings)"
                }
            )
        )

        // Reset for next build
        currentBuildId = null
    }



    // creates structtured error logs with the compiler messages we get from the api

    private fun emitCompileError(msg: com.intellij.openapi.compiler.CompilerMessage, severity: String) {
        val buildId = currentBuildId

        val vf = msg.virtualFile
        val filePath = vf?.path
        val msgText = msg.message.trim()

        if (msgText.isBlank()) return

        // Get line/column from CompilerMessageImpl (the actual implementation class)
        // this is technically internal api but it's the only way to get location info
        // degrades gracefully if cast fails - we just won't have line/column
        var msgLine: Int? = null
        var msgColumn: Int? = null

        if (msg is CompilerMessageImpl) {
            val line = msg.line
            if (line > 0) msgLine = line
            val column = msg.column
            if (column > 0) msgColumn = column
        }

        val normalizedMessage = msgText.replace(Regex("""\s+"""), " ").trim()
        val errorCategory = ErrorCategoryNormalizer.normalize(normalizedMessage)

        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "ErrorNormalized",
                lang = "java",
                phase = "compile",
                severity = severity,
                filePath = filePath,
                line = msgLine,
                column = msgColumn,
                errorCategory = errorCategory,
                errorType = normalizedMessage,
                fullMessage = normalizedMessage,
                buildId = buildId
            )
        )
    }


    // compile errors are handled, now for run time errors
    // runtime errors cant be gathered with api, have to parse

    fun onRunStarting(executorId: String, filename: String?) {
        currentRunId = PalgIds.newRunId()

        // clear buffer from any previous run
        consoleBuffer.clear()

        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "RunStart",
                runId = currentRunId,
                filename = filename,
                message = "executor=$executorId"
            )
        )
    }

    // accumulates console output into buffer
    // parsing happens all at once when process terminates
    fun onRunOutput(textChunk: String) {
        // cap buffer size to prevent oom from chatty programs
        if (consoleBuffer.length < MAX_BUFFER_SIZE) {
            val remaining = MAX_BUFFER_SIZE - consoleBuffer.length
            if (textChunk.length <= remaining) {
                consoleBuffer.append(textChunk)
            } else {
                consoleBuffer.append(textChunk.substring(0, remaining))
            }
        }
    }

    fun onRunTerminated(exitCode: Int) {
        // parse all exceptions from the complete console output
        // wrapped in try-catch so parsing failures don't lose the RunEnd event
        try {
            val exceptions = runtimeParser.parseConsoleOutput(consoleBuffer.toString())
            for (exception in exceptions) {
                emitRuntimeException(exception)
            }
        } catch (e: Exception) {
            // log the parse failure as an error event so we know something went wrong
            emit(
                ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "ErrorNormalized",
                    phase = "runtime",
                    severity = "error",
                    errorCategory = "parse_failure",
                    fullMessage = "Failed to parse console output: ${e.message}",
                    runId = currentRunId
                )
            )
        }
        consoleBuffer.clear()

        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "RunEnd",
                runId = currentRunId,
                message = "exitCode=$exitCode"
            )
        )
        currentRunId = null
    }


    /**
     * emits a structured event for a runtime exception
     * similar to compile errors, but has runtime-specific fields:
     */
    private fun emitRuntimeException(exception: RuntimeExceptionParser.ParsedRuntimeException) {
        val runId = currentRunId

        // for categorization, the exception class is the category
        val errorCategory = ErrorCategoryNormalizer.normalizeException(exception.exceptionClass)

        emit(
            ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "ErrorNormalized",
                lang = "java",
                phase = "runtime",
                severity = "error",
                filePath = exception.fileName,
                line = exception.line,
                column = null,
                errorCategory = errorCategory,
                errorType = exception.fullMessage,
                fullMessage = exception.fullMessage,
                runId = runId,
                stackTraceDepth = exception.stackTraceDepth,
                stackTrace = exception.fullStackTrace
            )
        )
    }


    /**
     * attaches a listener to a running process to capture its console output.
     *
     * called from PalgListener when a process starts. The ProcessListener
     * receives all console output and feeds it to our parser.
     */
    fun attachToProcess(handler: ProcessHandler) {
        handler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                // process started - nothing to do
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                // console output arrived - add to buffer
                onRunOutput(event.text)
            }

            override fun processTerminated(event: ProcessEvent) {
                // process ended - parse buffer and log termination
                onRunTerminated(event.exitCode)
            }
        })
    }


    /**
     * hooks into the ide message bus to know when a compilation finishes (we subscribe to CompilerTopics.COMPILATION_STATUS).
     * the connection is registered with Disposer so it gets cleaned up when the project closes
     */
    private fun installCompilerListener() {
        val connection = project.messageBus.connect()
        Disposer.register(this, connection)

        connection.subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
            override fun compilationFinished(
                aborted: Boolean,
                errors: Int,
                warnings: Int,
                compileContext: CompileContext
            ) {
                onCompilationFinished(aborted, errors, warnings, compileContext)
            }
        })
    }

    override fun dispose() {
    }
}
