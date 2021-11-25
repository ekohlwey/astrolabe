package com.github.ekohlwey.astrolabe.devices

import com.github.ekohlwey.astrolabe.HostMessage
import com.github.ekohlwey.astrolabe.HostMessage.HostMessageParseError
import com.github.ekohlwey.astrolabe.HostMessage.SuccessfulParse
import com.github.ekohlwey.astrolabe.PLUGIN_TODO
import com.github.ekohlwey.astrolabe.messages.DeviceMessage
import com.github.ekohlwey.astrolabe.messages.DeviceMessage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.io.*

class Console(private val consoleIn: Reader, private val consoleOut: PrintStream) : AutoCloseable {

    private fun PrintStream.bell() = this.print('\u0007')
    private fun PrintStream.backspace() {
        this.append('\b')
        this.append(' ')
        this.append('\b')
    }

    private val sentMessages = Channel<HostMessage>()
    private var currentUserLine = StringBuilder()

    private fun readNextChar(): Char? {
        val readChar = consoleIn.read()
        return if (readChar > -1) {
            readChar.toChar()
        } else {
            null
        }
    }

    val messages: Flow<HostMessage> = sentMessages.receiveAsFlow()

    suspend fun startReading() {
        withContext(Dispatchers.IO) {
            while (true) {
                val inChar = readNextChar() ?: break
                readIncomingConsole(inChar)
            }
        }
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
            is SuccessfulParse -> doIo { consoleOut.bell() }
            is HostMessageParseError -> doIo {
                consoleOut.appendLine()
                parseResult.printHelp(consoleOut)
                consoleOut.appendLine(currentUserLine)
            }
            else -> PLUGIN_TODO
        }
    }

    private suspend fun handleUserInput() {
        doIo {
            consoleOut.appendLine()
        }
        when (val parseResult = HostMessage.tryParseToEnd(currentUserLine)) {
            is SuccessfulParse -> sentMessages.send(parseResult.hostMessage)
            is HostMessageParseError -> doIo {
                consoleOut.bell()
                consoleOut.appendLine()
                parseResult.printHelp(consoleOut)
            }
            else -> PLUGIN_TODO
        }
        doIo {
            currentUserLine.clear()
        }
    }

    override fun close() {
        consoleIn.close()
        consoleOut.close()
    }

    suspend fun writeDeviceMessages(messages: Flow<DeviceMessage>) {
        logger.debug { "Processing device messages" }
        messages.collect { processMessage(it) }
    }

    private suspend fun processMessage(deviceMessage: DeviceMessage) {
        logger.trace { "Incoming device message: $deviceMessage" }
        doIo {
            when (deviceMessage) {
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
        private val logger = KotlinLogging.logger { }

    }

}

