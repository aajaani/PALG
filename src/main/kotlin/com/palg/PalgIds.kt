package com.palg

import java.util.concurrent.atomic.AtomicLong

/**
 * generates unique ids for builds and runs.
 *
 * all events from one compilation share a buildId,
 * all events from one execution share a runId.
 */
object PalgIds {

    //  thread-safe counter for uniqueness
    private val counter = AtomicLong(0)

    /**
     * creates a new id for a compilation.
     *
     * call this when a build starts.
     * all compile errors should reference this id.
     */
    fun newBuildId(): String =
        "build-${System.currentTimeMillis()}-${counter.incrementAndGet()}"

    /**
     * same but for runtime
     */
    fun newRunId(): String =
        "run-${System.currentTimeMillis()}-${counter.incrementAndGet()}"
}
