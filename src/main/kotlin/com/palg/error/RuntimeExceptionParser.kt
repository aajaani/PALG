package com.palg.error


// parses Java runtime exceptions from console output.

class RuntimeExceptionParser {

    data class ParsedRuntimeException(
        val exceptionClass: String,
        val detailMessage: String?,
        val fullMessage: String,
        val fileName: String?,
        val line: Int?,
        val stackTraceDepth: Int,
        val fullStackTrace: String
    )

    /**
     * parse complete console output for exceptions.
     * @param fullOutput complete console output from program run
     * @return list of parsed exceptions found in output
     */
    fun parseConsoleOutput(fullOutput: String): List<ParsedRuntimeException> {
        val results = mutableListOf<ParsedRuntimeException>()
        val lines = fullOutput.lines()

        var i = 0
        while (i < lines.size) {
            val header = tryParseHeader(lines[i])
            if (header != null) {
                // ollects stack frames
                val stackLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && lines[j].trim().startsWith("at ")) {
                    stackLines.add(lines[j])
                    j++
                }

                // also collect "Caused by:" and "... N more" lines
                while (j < lines.size &&
                    (lines[j].trim().startsWith("Caused by:") ||
                            lines[j].trim().matches(Regex("""^\.\.\. \d+ more$""")) ||
                            lines[j].trim().startsWith("at "))) {
                    stackLines.add(lines[j])
                    j++
                }

                if (stackLines.isNotEmpty()) {
                    results.add(buildException(header, lines[i], stackLines))
                }
                i = j
            } else {
                i++
            }
        }

        return results
    }

    private data class ExceptionHeader(
        val exceptionClass: String,
        val detailMessage: String?
    )

    private fun tryParseHeader(line: String): ExceptionHeader? {
        val trimmed = line.trim()

        // Pattern: "Exception in thread "main" java.lang.NullPointerException: message"
        val threadPattern = Regex(
            """Exception in thread\s+"[^"]+"\s+([a-zA-Z_][\w.]*(?:Exception|Error|Throwable))(?::\s*(.*))?"""
        )
        threadPattern.find(trimmed)?.let { match ->
            return ExceptionHeader(
                match.groupValues[1],
                match.groupValues[2].takeIf { it.isNotBlank() }
            )
        }

        // Pattern: "java.lang.ArrayIndexOutOfBoundsException: 5"
        val barePattern = Regex(
            """^([a-zA-Z_][\w.]*(?:Exception|Error|Throwable))(?::\s*(.*))?$"""
        )
        barePattern.find(trimmed)?.let { match ->
            return ExceptionHeader(
                match.groupValues[1],
                match.groupValues[2].takeIf { it.isNotBlank() }
            )
        }

        return null
    }

    /**
     * checks if a stack frame is from jdk/system code rather than user code.
     * we want to report the location in the student's code, not in Integer.parseInt etc.
     *
     * checks both module prefixes and package names ,
     *
     * for example this:
     * Exception in thread "main" java.lang.NumberFormatException: For input string: "abc"
     * 	at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
     * 	at java.base/java.lang.Integer.parseInt(Integer.java:662)
     * 	at java.base/java.lang.Integer.parseInt(Integer.java:778)
     * 	at Main.main(Main.java:3)
     *
     * 	could probably be simplified greatly
     */
    private fun isSystemFrame(stackLine: String, methodRef: String): Boolean {
        // module prefixes from java (like "java.base/java.lang.Integer.parseInt")
        if (stackLine.contains("java.base/") ||
            stackLine.contains("java.desktop/") ||
            stackLine.contains("jdk.internal/") ||
            stackLine.contains("jdk.proxy")) {
            return true
        }

        // extract the class name from the method reference
        // strip the module prefix if present, then get everything before the last dot
        val withoutModule = methodRef.substringAfter("/")
        val className = withoutModule.substringBeforeLast(".")

        // check if class is in standard library packages
        return className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("jdk.") ||
                className.startsWith("sun.") ||
                className.startsWith("com.sun.")
    }

    private fun buildException(
        header: ExceptionHeader,
        headerLine: String,
        stackLines: List<String>
    ): ParsedRuntimeException {
        // find first stack frame from user code (skip JDK internals)
        var fileName: String? = null
        var lineNumber: Int? = null

        val framePattern = Regex("""at\s+(.+)\(([^():]+\.java):(\d+)\)""")

        for (stackLine in stackLines) {
            framePattern.find(stackLine)?.let { match ->
                val methodRef = match.groupValues[1]
                // skip jdk/system frames - we want student code location
                if (!isSystemFrame(stackLine, methodRef)) {
                    fileName = match.groupValues[2]
                    lineNumber = match.groupValues[3].toInt()
                }
            }
            if (fileName != null) break
        }

        // if no user code found, use first frame with file info
        // this can happen for errors thrown entirely within jdk code, without it filename would be exception class sometimes
        if (fileName == null) {
            for (stackLine in stackLines) {
                framePattern.find(stackLine)?.let { match ->
                    fileName = match.groupValues[2]
                    lineNumber = match.groupValues[3].toInt()
                }
                if (fileName != null) break
            }
        }

        // count "at .." frames for depth
        val stackDepth = stackLines.count { it.trim().startsWith("at ") }

        val fullTrace = (listOf(headerLine) + stackLines).joinToString("\n")
        val fullMessage = headerLine.trim().replace(Regex("""\s+"""), " ")

        return ParsedRuntimeException(
            exceptionClass = header.exceptionClass,
            detailMessage = header.detailMessage,
            fullMessage = fullMessage,
            fileName = fileName,
            line = lineNumber,
            stackTraceDepth = stackDepth,
            fullStackTrace = fullTrace
        )
    }
}
