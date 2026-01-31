package com.palg

import com.google.gson.GsonBuilder
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFile
import com.palg.PalgUtils.Companion.getUUIDFromString
import com.palg.model.ActivityData
import mu.KotlinLogging


class PalgListener : FileEditorManagerListener, DocumentListener, CopyPastePreProcessor, ExecutionListener,
    BulkFileListener {

    private val logger = KotlinLogging.logger {}
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    // check if java module is available (assures PALG can have compatibility with other jetBrains IDE, not just intellij.)
    private fun isJavaAvailable(): Boolean {
        return try {
            Class.forName("com.intellij.lang.java.JavaLanguage")
            true
        } catch (_: Throwable) {
            false
        }
    }

    // null if Java module not available
    private fun getCompilerService(project: Project): Any? {
        return try {
            val serviceClass = Class.forName("com.palg.service.PalgCompilerService")
            project.getService(serviceClass)
        } catch (_: Throwable) {
            null
        }
    }

    override fun documentChanged(event: DocumentEvent) {
        val oldLength = event.oldLength
        val newLength = event.newLength
        val changedVirtualFileURL = PalgUtils.getVirtualFileURLByDocument(event.document) ?: ""
        val virtualFile = PalgUtils.getVirtualFileByDocument(event.document)

        if(changedVirtualFileURL.startsWith("https:")){
            return
        }

        if (newLength > oldLength) {
            if(event.newFragment.toString().startsWith("IntellijIdeaRulezzz")){
                return
            }
            if(virtualFile == null){ // ShellText event
                val activityData = ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "TextInsert",
                    text = event.newFragment.toString(),
                    textWidgetClass = "ShellText",
                    index = PalgUtils.getIndex(event, event.offset)
                )
                logger.info { gson.toJson(activityData) }
            }else if(!virtualFile.url.startsWith("mock:")){ // CodeViewText event (editor)
                val activityData = ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "TextInsert",
                    text = event.newFragment.toString(),
                    textWidgetClass = "CodeViewText",
                    textWidgetId = PalgUtils.getVirtualFileUUIDByDocument(event.document),
                    index = PalgUtils.getIndex(event, event.offset)
                )
                logger.info { gson.toJson(activityData) }
            }

        } else if (newLength < oldLength) {
            if(virtualFile == null) { // ShellText deletion
                val activityData = ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "TextDelete",
                    textWidgetClass = "ShellText",
                    index1 = PalgUtils.getIndex(event, event.offset),
                    index2 = PalgUtils.getIndex2(event, event.offset)
                )
                logger.info { gson.toJson(activityData) }
            } else if(!virtualFile.url.startsWith("mock:")) { // CodeViewText deletion (editor)
                val activityData = ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "TextDelete",
                    textWidgetClass = "CodeViewText",
                    textWidgetId = PalgUtils.getVirtualFileUUIDByDocument(event.document),
                    index1 = PalgUtils.getIndex(event, event.offset),
                    index2 = PalgUtils.getIndex2(event, event.offset)
                )
                logger.info { gson.toJson(activityData) }
            }
        }
    }

    override fun preprocessOnCopy(file: PsiFile?, startOffsets: IntArray?, endOffsets: IntArray?, text: String?): String? = null

    override fun preprocessOnPaste(project: Project?, file: PsiFile?, editor: Editor?, text: String?, rawText: RawText?): String {
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "<<Paste>>",
            textWidgetClass = "CodeViewText",
            textWidgetId = editor?.let { PalgUtils.getVirtualFileUUIDByDocument(it.document) } ?: ""
        )
        logger.info { gson.toJson(activityData) }
        return text ?: ""
    }

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            when (event) {
                is VFileCreateEvent -> handleFileCreation(event)
                is VFileDeleteEvent -> handleFileDeletion(event)
            }
        }
    }

    fun handleFileCreation(event: VFileCreateEvent) {
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "fileCreated",
            textWidgetId = event.file?.let { getUUIDFromString(it.url) },
            filename = event.file?.name
        )
        logger.info { gson.toJson(activityData) }
    }

    fun handleFileDeletion(event: VFileDeleteEvent) {
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "fileDeleted",
            textWidgetId = getUUIDFromString(event.file.url),
            filename = event.file.name
        )
        logger.info { gson.toJson(activityData) }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        // initialize compiler service if java module is available
        getCompilerService(source.project)

        super.fileOpened(source, file)
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "Open",
            textWidgetClass = "CodeViewText",
            textWidgetId = getUUIDFromString(file.url),
            filename = file.name
        )
        logger.info { gson.toJson(activityData) }

        val editor = source.getSelectedEditor(file)
        if (editor is TextEditor) {
            val textEditor: TextEditor = editor
            val document: Document = textEditor.editor.document
            val activityDataTextInsert = ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "FileContent",
                text = document.text,
                textWidgetClass = "CodeViewText",
                textWidgetId = getUUIDFromString(file.url),
                index = "1.0"
            )
            logger.info { gson.toJson(activityDataTextInsert) }
        }
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "Close",
            textWidgetClass = "CodeViewText",
            textWidgetId = getUUIDFromString(file.url),
            filename = file.name
        )
        logger.info { gson.toJson(activityData) }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "<Button-1>",
            textWidgetClass = "CodeViewText",
            textWidgetId = event.newFile?.url?.let { getUUIDFromString(it) }
        )
        logger.info { gson.toJson(activityData) }
    }

    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        super.processStarting(executorId, env, handler)
        val editor = FileEditorManager.getInstance(env.project).selectedTextEditor
        val file = editor?.let { PalgUtils.getVirtualFileByDocument(it.document) }
        val activityData = ActivityData(
            time = PalgUtils.getCurrentDateTime(),
            sequence = "ShellCommand",
            commandText = "%${executorId} ${file?.name}",
        )
        logger.info { gson.toJson(activityData) }

        // hook into compiler service if available
        getCompilerService(env.project)?.let { service ->
            try {
                val serviceClass = service.javaClass
                serviceClass.getMethod("onRunStarting", String::class.java, String::class.java)
                    .invoke(service, executorId, file?.name)
                serviceClass.getMethod("attachToProcess", ProcessHandler::class.java)
                    .invoke(service, handler)
            } catch (_: Throwable) {

            }
        }
    }
}

class PalgStartupActivity : ProjectActivity {
    private val logger = KotlinLogging.logger {}
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    override suspend fun execute(project: Project) {
        // capture any files already open when project starts
        val fileEditorManager = FileEditorManager.getInstance(project)
        for (file in fileEditorManager.openFiles) {
            val activityData = ActivityData(
                time = PalgUtils.getCurrentDateTime(),
                sequence = "Open",
                textWidgetClass = "CodeViewText",
                textWidgetId = PalgUtils.getUUIDFromString(file.url),
                filename = file.name
            )
            logger.info { gson.toJson(activityData) }

            // log file content
            val editor = fileEditorManager.getSelectedEditor(file)
            if (editor is TextEditor) {
                val document = editor.editor.document
                val contentData = ActivityData(
                    time = PalgUtils.getCurrentDateTime(),
                    sequence = "FileContent",
                    text = document.text,
                    textWidgetClass = "CodeViewText",
                    textWidgetId = PalgUtils.getUUIDFromString(file.url),
                    index = "1.0"
                )
                logger.info { gson.toJson(contentData) }
            }
        }

        // initialize compiler service if available
        try {
            val serviceClass = Class.forName("com.palg.service.PalgCompilerService")
            project.getService(serviceClass)
        } catch (_: Throwable) {}
    }
}