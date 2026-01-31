package com.palg.model

import com.google.gson.annotations.SerializedName

data class ActivityData(
    @SerializedName("time") val time: String,
    @SerializedName("sequence") val sequence: String,
    @SerializedName("text_widget_id") val textWidgetId: String? = null,
    @SerializedName("text_widget_class") val textWidgetClass: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("index") val index: String? = null,
    @SerializedName("index1") val index1: String? = null,
    @SerializedName("index2") val index2: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("command_text") val commandText: String? = null,

    // build/run
    @SerializedName("message") val message: String? = null,
    @SerializedName("build_id") val buildId: String? = null,
    @SerializedName("run_id") val runId: String? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("error_count") val errorCount: Int? = null,
    @SerializedName("warning_count") val warningCount: Int? = null,

    //errors
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("line") val line: Int? = null,
    @SerializedName("column") val column: Int? = null,
    @SerializedName("error_category") val errorCategory: String? = null,
    @SerializedName("error_type") val errorType: String? = null,
    @SerializedName("full_message") val fullMessage: String? = null,
    @SerializedName("stack_trace_depth") val stackTraceDepth: Int? = null,
    @SerializedName("stack_trace") val stackTrace: String? = null
)
