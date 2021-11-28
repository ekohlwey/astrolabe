package com.github.ekohlwey.astrolabe.devices

import com.github.ekohlwey.astrolabe.Direction
import com.github.ekohlwey.astrolabe.Direction.backward
import com.github.ekohlwey.astrolabe.Direction.forward
import com.github.ekohlwey.astrolabe.HostMessage
import com.github.ekohlwey.astrolabe.HostMessage.Companion
import com.github.ekohlwey.astrolabe.HostMessage.Companion.completionCandidates
import com.github.ekohlwey.astrolabe.HostMessage.HostMessageParseError
import com.github.ekohlwey.astrolabe.HostMessage.SuccessfulParse
import com.github.ekohlwey.astrolabe.PLUGIN_TODO
import com.github.ekohlwey.astrolabe.doIo
import com.github.ekohlwey.astrolabe.messages.DeviceMessage
import com.github.ekohlwey.astrolabe.messages.DeviceMessage.*
import com.github.h0tk3y.betterParse.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.fusesource.jansi.Ansi
import org.jline.reader.*
import org.jline.reader.Parser
import org.jline.reader.Parser.ParseContext.ACCEPT_LINE
import org.jline.reader.Parser.ParseContext.COMPLETE
import org.jline.terminal.Terminal
import org.jline.terminal.Terminal.Signal
import org.jline.terminal.Terminal.Signal.INT
import org.jline.terminal.Terminal.Signal.QUIT
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp
import org.jline.utils.InfoCmp.*
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter

class Console(private val consoleIn: InputStream, private val consoleOut: OutputStream) : AutoCloseable {

    private val sentMessages = Channel<HostMessage>()
    private val internalErrors = Channel<ErrorResult>(1)
    private var terminal: Terminal? = null
    private var reader: LineReader? = null
    val messages: Flow<HostMessage> = sentMessages.receiveAsFlow()


    data class AstrolabeLine(
        val word: String,
        val wordCursor: Int,
        val wordIndex: Int,
        val words: List<String>,
        val line: String,
        val cursor: Int,
        val rawWordCursor: Int,
        val rawWordLength: Int,
        val completions: List<String>
    ) :
        CompletingParsedLine {
        override fun word() = word
        override fun wordCursor(): Int = wordCursor
        override fun wordIndex(): Int = wordIndex
        override fun words(): List<String> = words
        override fun line(): String = line
        override fun cursor(): Int = cursor
        override fun escape(candidate: CharSequence, complete: Boolean): CharSequence = candidate
        override fun rawWordCursor() = rawWordCursor
        override fun rawWordLength() = rawWordLength
    }

    class AstrolabeParser(val terminal: Terminal, val internalErrors: Channel<ErrorResult>) : Parser {
        override fun parse(line: String, cursor: Int, context: Parser.ParseContext): ParsedLine {
            val matches = HostMessage.grammar.tokenizer.tokenize(line)
            val lastToken = matches.lastOrNull()
            val lastRow = lastToken?.row ?: 0
            val lastColumn = (lastToken?.column ?: 0) + (lastToken?.length ?: 0)
            val result = HostMessage.grammar.tryParseToEnd(matches, 0)
            if (result is Parsed<HostMessage> || context == COMPLETE) {
                val word = matches.lastOrNull()
                val lastNonIgnored = matches.filter { !it.type.ignored }.lastOrNull()
                val extraCandidates = if (word != lastNonIgnored) {
                    when (lastNonIgnored?.text) {
                        "move" -> listOf(" steps =", " ${forward.name}", " ${backward.name}")
                        "step" -> listOf(" steps =", " ${forward.name}", " ${backward.name}", " current =")
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }
                val line = AstrolabeLine(
                    word?.text ?: "",
                    cursor - (word?.offset ?: 0),
                    (word?.tokenIndex) ?: 0,
                    matches.map { it.text }.toList(),
                    line,
                    cursor,
                    word?.length ?: 0,
                    word?.length ?: 0,
                    ((result as? ErrorResult)?.completionCandidates ?: emptyList()) + extraCandidates
                )
                logger.trace { line }
                return line
            } else {
                if (context == ACCEPT_LINE) {
                    runBlocking {
                        internalErrors.send(result as ErrorResult)
                    }
                }
                throw generateSyntaxError(result as ErrorResult, lastRow, lastColumn)
            }
        }


        private fun generateSyntaxError(result: ErrorResult, lastRow: Int, lastColumn: Int): SyntaxError {
            when (result) {
                is HostMessage.Companion.OneExpected -> {
                    val options = result.failedOptions.map { (it.token.name) }.joinToString(", ")
                    return if (result.failedOptions.all { it.source is UnexpectedEof }) {
                        EOFError(lastRow, lastColumn, "Unexpected <EOF>, expected one of [$options]")
                    } else if (result.failedOptions.all { it.source is MismatchedToken || it.source is NoMatchingToken }) {
                        val (found, row, column) = result.failedOptions.map { it.source }.first().let {
                            when (it) {
                                is MismatchedToken -> Triple(it.found.text, it.found.row, it.found.column)
                                is NoMatchingToken -> Triple(
                                    it.tokenMismatch.text,
                                    it.tokenMismatch.row,
                                    it.tokenMismatch.column
                                )
                                else -> null
                            }
                        }!!
                        SyntaxError(row, column, "Unexpected ${found}, expected one of ${options}")
                    } else {
                        SyntaxError(
                            0,
                            0,
                            "Expected one of [$options]"
                        )
                    }
                }
                is UnparsedRemainder -> return SyntaxError(
                    result.startsWith.row,
                    result.startsWith.column,
                    "Unexpected input ${result.startsWith.text}, expected <EOF>"
                )
                is NoMatchingToken -> return SyntaxError(
                    result.tokenMismatch.row,
                    result.tokenMismatch.column,
                    "Unexpected input ${result.tokenMismatch.text}"
                )
                is MismatchedToken -> return SyntaxError(
                    result.found.row,
                    result.found.column,
                    "Unexpected input ${result.found.text}, expected ${result.expected.name}"
                )
                is UnexpectedEof -> return EOFError(
                    lastRow,
                    lastColumn,
                    "Unexpected <EOF>, expected ${result.expected.name}"
                )
                else -> return SyntaxError(0, 0, "Unparseable input")
            }
        }
    }

    object AstrolabeCompleter : Completer {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
            (line as? AstrolabeLine)?.let {
                candidates.addAll(it.completions.map { Candidate(it) })
            }
        }

    }

    suspend fun startReading() {
        if (terminal != null) throw IllegalStateException("Cannot open a terminal twice")
        terminal =
            TerminalBuilder.builder()
                .jansi(true)
                .let {
                    // only set streams if they are not stdin and stdout
                    if (consoleIn != System.`in` || consoleOut != System.out) it.streams(
                        consoleIn,
                        consoleOut
                    ) else it.system(true)
                }
                .nativeSignals(true)
                .signalHandler { runBlocking { handleSignal(it) } }
                .let { doIo { it.build() } ?: throw IllegalStateException("Unable to open terminal") }

        reader = LineReaderBuilder.builder()
            .appName("astrolabe")
            .parser(AstrolabeParser(terminal!!, internalErrors))
            .completer(AstrolabeCompleter)
            .terminal(terminal)
            .build()

        withContext(Dispatchers.IO) {
            launch {
                internalErrors.receiveAsFlow().collect { writeConsoleError(it) }
            }
            while (true) {
                val result = withContext(Dispatchers.IO) { runCatching { reader?.readLine(" > ") ?: "" } }
                when (val exception = result.exceptionOrNull()) {
                    is InterruptedException, is UserInterruptException -> continue
                    is EOFException, is EndOfFileException -> {
                        close()
                        break
                    }
                    null -> {
                        /* no exception - get and assert the value below*/
                    }
                    else -> {
                        logger.info(exception) { "Exception while reading next line" }
                        close()
                        break
                    }
                }
                val line = result.getOrThrow()
                if (line.trim().equals("")) {
                    continue
                }
                handleUserInput(line)
            }
        }
    }

    private suspend fun handleSignal(signal: Signal) {
        when (signal) {
            INT -> handleCtlC()
            QUIT -> close()
        }
    }

    private suspend fun handleCtlC() {
        reader?.callWidget(LineReader.FRESH_LINE)
//        aboveLine { /* do nothing - just redraw the prompt */ }
    }

    private suspend fun writeConsoleError(errorResult: ErrorResult) {
        aboveLine { writer -> recursivePrintError(0, errorResult, writer) }
    }

    private fun recursivePrintError(indent: Int, errorResult: ErrorResult, writer: PrintWriter) {
        repeat(indent) { writer.print(' ') }
        when (errorResult) {
            is HostMessage.Companion.ExpectedToken -> writer.println("Bad input ${errorResult.mismatchText}, expected ${errorResult.token.name}")
            is HostMessage.Companion.OneExpected -> {
                val foundText = errorResult.failedOptions.first().mismatchText
                writer.println(
                    "Bad input ${foundText ?: "<EOF>"}, expected one of [${
                        errorResult.failedOptions.map { it.token.name }.joinToString(", ")
                    }]"
                )
            }
            is AlternativesFailure -> {
                writer.println("Couldn't parse for several reasons:")
                errorResult.errors.forEach { recursivePrintError(indent + 1, it, writer) }
            }
            is UnparsedRemainder -> {
                writer.println("Bad input ${errorResult.startsWith.text}")
            }
            is NoMatchingToken -> {
                writer.println("Bad input ${errorResult.tokenMismatch.text}")
            }
            is MismatchedToken -> {
                writer.println("Bad input ${errorResult.found.text}, expected ${errorResult.expected.name}")
            }
            is UnexpectedEof -> {
                writer.println("Bad input <EOF> expected ${errorResult.expected.name}")
            }
            else -> {
                writer.println("Bad input")
            }
        }
    }

    private suspend fun handleUserInput(line: String) {
        when (val parseResult = HostMessage.tryParseToEnd(line)) {
            is SuccessfulParse -> sentMessages.send(parseResult.hostMessage)
            is HostMessageParseError -> doIo {
                throw IllegalStateException("Terminal should have caught error")
            }
            else -> PLUGIN_TODO
        }
    }

    override fun close() {
        if (consoleIn != System.`in`) consoleIn.close()
        if (consoleOut != System.out) consoleOut.close()
        terminal?.close()
        internalErrors.close()
        sentMessages.close()
    }

    suspend fun writeDeviceMessages(messages: Flow<DeviceMessage>) {
        logger.debug { "Processing device messages" }
        messages.collect { processMessage(it) }
    }

    private suspend fun aboveLine(writeFn: (PrintWriter) -> Unit) {
        doIo {
            val writer =
                terminal?.writer() ?: throw IllegalStateException("Must start reading terminal to process messages")
            terminal?.puts(Capability.carriage_return)
            writeFn(writer)
            reader?.callWidget(LineReader.REDRAW_LINE)
            reader?.callWidget(LineReader.REDISPLAY)
            writer.flush()
        }
    }

    private suspend fun processMessage(deviceMessage: DeviceMessage) {
        logger.trace { "Incoming device message: $deviceMessage" }
        aboveLine { writer ->
            when (deviceMessage) {
                is DeviceCalibrate -> writer.println("calibrate = ${deviceMessage.calibrate.name}")
                is DeviceKp -> writer.println("kp = ${deviceMessage.kp.value}")
                is DeviceKi -> writer.println("ki = ${deviceMessage.ki.value}")
                is DeviceKd -> writer.println("kd = ${deviceMessage.kd.value}")
                is DeviceCurrent -> writer.println("current = ${deviceMessage.currentMillis.amount}")
                is DeviceStepSize -> writer.println("step_size = ${deviceMessage.size.amount}")
                is DeviceEnable -> writer.println("enabled = ${deviceMessage.enabled.name}")
                is DeviceMotorDir -> writer.println("direction = ${deviceMessage.motorDir.name}")
                is DeviceAngle -> writer.println("angle = ${deviceMessage.angle.measurement}")
                is DeviceAngleError -> writer.println("angle_error = ${deviceMessage.angleError.measurement}")
                is DeviceStatus -> {
                    writer.println("enabled = ${deviceMessage.enabled.name}")
                    writer.println("close_loop = ${deviceMessage.mode.name}")
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }

}

