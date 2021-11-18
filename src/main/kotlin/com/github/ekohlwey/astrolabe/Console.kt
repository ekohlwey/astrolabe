package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.messages.DeviceMessage
import com.github.ekohlwey.astrolabe.messages.DeviceMessage.*
import com.github.h0tk3y.betterParse.parser.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.Reader

class Console(private val consoleIn: Reader, private val consoleOut: PrintWriter) : AutoCloseable {

    private fun PrintWriter.bell() = this.print('\u0007')
    private fun PrintWriter.backspace() {
        this.append('\b')
        this.append(' ')
        this.append('\b')
    }
    private val sentMessages = Channel<HostMessage>()
    private var currentUserLine = StringBuilder()
    private val inputSequece = sequence {
        while (true) {
            val inChar = runCatching {
                runBlocking(Dispatchers.IO) { consoleIn.read().toChar() }
            }.getOrNull() ?: break
            yield(inChar)
        }
    }

    val messages: Flow<HostMessage> = sentMessages.receiveAsFlow()

    private suspend fun startReading() {
        withContext(Dispatchers.Default) {
            launch { inputSequece.forEach { readIncomingConsole(it) } }
        }
    }

    private fun printUserErrorHelp(errorResult: ErrorResult, indent: Int) {
        repeat(indent) { consoleOut.append(' ') }
        when (errorResult) {
            is AlternativesFailure -> {
                consoleOut.appendLine("Tried several alternatives, each resulted in an error:")
                errorResult.errors.forEach { printUserErrorHelp(it, indent + 2) }
            }
            is UnparsedRemainder -> {
                consoleOut.appendLine("${errorResult.startsWith.text} was unexpected. The text before this is already a complete command.")
            }
            is NoMatchingToken -> {
                consoleOut.appendLine("${errorResult.tokenMismatch.text} was unexpected. It is not part of a command.")
            }
            is MismatchedToken -> {
                consoleOut.appendLine("Expected a ${errorResult.expected.name}, but found ${errorResult.found.text} instead.")
            }
            is UnexpectedEof -> {
                consoleOut.appendLine("Incomplete command. Looking for a ${errorResult.expected.name} next.")
            }
        }
        consoleOut.flush()
    }

    private suspend fun readIncomingConsole(inChar: Char) {
        // the only character not printed is tab
        when (inChar) {
            '\n' -> handleUserInput()
            '\t' -> suggestToUser()
            '\b' -> handleBackspace()
            else -> printAndExtendCurrentLine(inChar)
        }

    }

    private suspend inline fun <T> doIo(crossinline closure: () -> T?) {
        return withContext(Dispatchers.IO) { runCatching { closure.invoke() }.getOrNull() }
    }

    private suspend fun printAndExtendCurrentLine(inChar: Char) {
        doIo {
            consoleOut.append(inChar)
            currentUserLine.append(inChar)
        }
    }

    private suspend fun handleBackspace() {
        doIo {
            consoleOut.backspace()
            currentUserLine.deleteCharAt(currentUserLine.length - 1)
        }
    }

    private suspend fun suggestToUser() {
        when (val parseResult = HostMessage.tryParseToEnd(currentUserLine)) {
            is Parsed -> doIo { consoleOut.bell() }
            is ErrorResult -> doIo {
                consoleOut.appendLine()
                printUserErrorHelp(parseResult, 0)
                consoleOut.appendLine(currentUserLine)
            }
        }
    }

    private suspend fun handleUserInput() {
        doIo {
            consoleOut.appendLine()
            currentUserLine.clear()
        }
        when (val parseResult = HostMessage.tryParseToEnd(currentUserLine)) {
            is Parsed -> sentMessages.send(parseResult.value)
            is ErrorResult -> doIo {
                consoleOut.bell()
                consoleOut.appendLine()
                printUserErrorHelp(parseResult, 0)
            }
        }
    }

    override fun close() {
        consoleIn.close()
        consoleOut.close()
    }

    fun writeDeviceMessages(messages: Flow<DeviceMessage>) : Job =
        runBlocking {
            launch {
                messages.collect { processMessage(it) }
            }
        }

    private suspend fun processMessage(deviceMessage: DeviceMessage) {
        doIo {
            when(deviceMessage) {
                is DeviceCalibrate -> consoleOut.println("calibrate = ${deviceMessage.calibrate}")
                is DeviceKp -> consoleOut.println("kp = ${deviceMessage.kp}")
                is DeviceKi -> consoleOut.println("ki = ${deviceMessage.ki}")
                is DeviceKd -> consoleOut.println("kd = ${deviceMessage.kd}")
                is DeviceCurrent -> consoleOut.println("current = ${deviceMessage.currentMillis.amount}")
                is DeviceStepSize -> consoleOut.println("step_size = ${deviceMessage.size.amount}")
                is DeviceEnable -> consoleOut.println("enabled = ${deviceMessage.enabled}")
                is DeviceMotorDir -> consoleOut.println("direction = ${deviceMessage.motorDir}")
                is DeviceAngle -> consoleOut.println("angle = ${deviceMessage.angle.measurement}")
                is DeviceAngleError -> consoleOut.println("angle_error = ${deviceMessage.angleError.measurement}")
                is DeviceStatus -> {
                    consoleOut.println("enabled = ${deviceMessage.enabled}")
                    consoleOut.println("close_loop = ${deviceMessage.mode}")
                }
            }
        }
    }

    companion object {
        fun open(consoleIn: InputStream, consoleOut: OutputStream):Console {
            val console = Console(consoleIn.reader(), PrintWriter(consoleOut))
            runBlocking {
                launch {
                    console.startReading()
                }
            }
            return console
        }
    }

}

