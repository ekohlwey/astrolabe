@file:OptIn(ExperimentalUnsignedTypes::class)

package com.github.ekohlwey.astrolabe.devices

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortInvalidPortException
import com.github.ekohlwey.astrolabe.*
import com.github.ekohlwey.astrolabe.Direction.backward
import com.github.ekohlwey.astrolabe.Direction.forward
import com.github.ekohlwey.astrolabe.HostMessage.*
import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.PLUGIN_TODO
import com.github.ekohlwey.astrolabe.StreamAngleMode.silent
import com.github.ekohlwey.astrolabe.StreamAngleMode.stream
import com.github.ekohlwey.astrolabe.devices.TSDevice.DeviceMessageType.*
import com.github.ekohlwey.astrolabe.messages.*
import com.github.ekohlwey.astrolabe.messages.Calibrate.calibrated
import com.github.ekohlwey.astrolabe.messages.Calibrate.uncalibrated
import com.github.ekohlwey.astrolabe.messages.DeviceMessage.*
import com.github.ekohlwey.astrolabe.messages.EnableMode.disabled
import com.github.ekohlwey.astrolabe.messages.EnableMode.enabled
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer


class TSDevice private constructor(
    private val serialIn: InputStream,
    private val serialOut: OutputStream
) : Device {

    private val logger = KotlinLogging.logger {}
    private val internalMessages = Channel<DeviceMessage>()
    override val messages = internalMessages.receiveAsFlow()


    override suspend fun writeHostMessages(hostMessages: Flow<HostMessage>) {
        val sequences = generateSequence((0u).toUByte()) { (it + 1u).toUByte() }.asFlow()
        hostMessages.zip(sequences) { msg, seq -> writeMessage(msg, seq) }.collect {}
    }


    override fun close() {
        serialIn.close()
        serialOut.close()
        internalMessages.close()
    }


    private val GetParameter.paramId: UByte
        get() = when (this) {
            is GetCalibrate -> 0u
            is GetPidKp -> 1u
            is GetPidKi -> 2u
            is GetPidKd -> 3u
            is GetCurrent -> 4u
            is GetStepSize -> 5u
            is GetEnabled -> 6u
            is GetMotorDir -> 7u
            else -> PLUGIN_TODO
        }

    private val ReadValue.sourceId: UByte
        get() = when (this) {
            is ReadStatus -> 0u
            is ReadAngle -> 1u
            is ReadAngleError -> 2u
            else -> PLUGIN_TODO
        }

    private val SetParameter.paramId: UByte
        get() = when (this) {
            is SetCalibrate -> 0u
            is SetPidKp -> 1u
            is SetPidKi -> 2u
            is SetPidKd -> 3u
            is SetCurrent -> 4u
            is SetStepSize -> 5u
            is SetEnable -> 6u
            is SetMotorDir -> 7u
            else -> PLUGIN_TODO
        }

    private val StreamAngleMode.id: UByte
        get() = when (this) {
            stream -> 1u
            silent -> 0u
        }

    private val Direction.id: UByte
        get() = when (this) {
            forward -> 1u
            backward -> 0u
        }

    private val Direction.shortValue: Short
        get() = when (this) {
            forward -> 1
            backward -> 0
        }

    private val HostMessage.messageId: UByte
        get() = when (this) {
            is ReadValue -> 1u
            is GetParameter -> 2u
            is SetParameter -> 3u
            is Command -> 4u
            else -> PLUGIN_TODO
        }

    private val Calibrate.shortValue: Short
        get() = when (this) {
            calibrated -> 0
            uncalibrated -> 0xAA
        }

    private val EnableMode.shortValue: Short
        get() = when (this) {
            enabled -> 1
            disabled -> 0
        }

    private val SetParameter.value: Short
        get() = when (this) {
            is SetCalibrate -> this.calibrate.shortValue
            is SetPidKp -> this.pidKp.value.toShort()
            is SetPidKi -> this.pidKi.value.toShort()
            is SetPidKd -> this.pidKd.value.toShort()
            is SetCurrent -> this.current.amount.toShort()
            is SetStepSize -> this.steps.amount.toShort()
            is SetEnable -> this.enabled.shortValue
            is SetMotorDir -> this.motorDir.shortValue
            else -> PLUGIN_TODO
        }

    private val Command.commandId: UByte
        get() = when (this) {
            is Step -> 0u
            is StepForwardCommand -> 1u
            is StepBackwardCommand -> 2u
            is MoveCommand -> 3u
            is StorageSave -> 4u
            is ModeEnable -> 10u
            is ModeDisable -> 11u
            is ModeCloseloop -> 12u
            is ModeOpenloop -> 13u
            is StreamAngle -> 20u
            is JumpBootloader -> 30u
            else -> PLUGIN_TODO
        }

    private suspend fun writeMessage(msg: HostMessage, seq: UByte) {
        val buffer = ByteBuffer.allocate(16)
        buffer.putUByte(msg.messageId)
        when (msg) {
            is ReadValue -> buffer.putUByte(msg.sourceId)
            is GetParameter -> buffer.putUByte(msg.paramId)
            is SetParameter -> {
                buffer.putUByte(msg.paramId)
                buffer.putShort(msg.value)
            }
            is Command -> {
                buffer.putUByte(msg.commandId)
                when (msg) {
                    is StreamAngle -> buffer.putUByte(msg.mode.id)
                    is StorageSave, is ModeEnable, is ModeDisable, is ModeCloseloop, is ModeOpenloop, is JumpBootloader -> {
                    }
                    is Step -> buffer.putUByte(msg.direction.id)
                    is StepForwardCommand -> {
                        buffer.putUShort((msg as StepCommandDetails).step.amount.toUShort())
                        buffer.putUShort((msg as StepCommandDetails).current.amount.toUShort())
                    }
                    is StepBackwardCommand -> {
                        buffer.putUShort((msg as StepCommandDetails).step.amount.toUShort())
                        buffer.putUShort((msg as StepCommandDetails).current.amount.toUShort())
                    }
                    is MoveCommand -> {
                        buffer.putUShort(msg.steps.amount.toUShort())
                        buffer.putUByte(msg.direction.id)
                    }
                    else -> PLUGIN_TODO
                }
            }
            else -> PLUGIN_TODO
        }
        val len: UByte = buffer.position().toUByte()
        val seqAndLen = ((seq shl 4) and 0xF0u) or (len and 0x0Fu)
        val crc = generateCrc(ByteArrayInputStream(buffer.array(), 0, buffer.position()))
        withContext(Dispatchers.IO) {
            val error = kotlin.runCatching {
                serialOut.write(0xFE)
                serialOut.write(seqAndLen.toInt())
                serialOut.write(buffer.array(), 0, buffer.position())
                serialOut.write(crc.toInt())
            }.exceptionOrNull()
            if (error != null) logger.error(error) { "Serial output closed" }
        }
    }

    private data class SequenceAndLength(val sequence: UByte, val length: Int) {
        companion object {
            fun unpack(seqAndLen: UByte): SequenceAndLength {
                val seq = seqAndLen shr 4
                val len = seqAndLen.toInt() and 0x0f
                return SequenceAndLength(seq, len)
            }
        }
    }

    private tailrec suspend fun readIncomingSerial(
        serialIn: InputStream,
        checkSeq: UByte
    ) {
        val preamble = serialIn.readUByte() ?: return
        if (preamble.toUInt() != 0xFEu) {
            throw IllegalStateException("Invalid preamble: 0x${preamble.toString(16)}")
        }
        val seqAndLen = serialIn.readUByte() ?: return
        val (seq, len) = SequenceAndLength.unpack(seqAndLen)
        if (checkSeq != seq) {
            logger.warn { "Out of sequence packet, should have been $checkSeq but was $seq" }
        }
        val messageId = serialIn.readUByte() ?: return
        val payload = serialIn.readUBytes(len) ?: return
        val crc = serialIn.readUByte() ?: return
        val crcComputePacket = with(ByteBuffer.allocate(2 + len)) {
            putUByte(seqAndLen)
            putUByte(messageId)
            putUBytes(payload)
        }
        val computedCrc = generateCrc(ByteArrayInputStream(crcComputePacket.array()))
        if (computedCrc != crc) {
            logger.warn { "Invalid CRC. Computed crc was $computedCrc but packet crc was $crc." }
        }
        val message = decodeMessage(messageId, payload) ?: return
        internalMessages.send(message)
        val nextSeq = if (seq == UByte.Companion.MAX_VALUE) 0u else (seq + 1u).toUByte()
        readIncomingSerial(serialIn, nextSeq)
    }


    enum class DeviceMessageType(val byteValue: UByte) {
        // these are response message ID's
        PARAM_CALIBRATE(10u),
        PARAM_KP(11u),
        PARAM_KI(12u),
        PARAM_KD(13u),
        PARAM_CURRENT(14u),
        PARAM_STEPSIZE(15u),
        PARAM_ENDIR(16u),
        PARAM_MOTORDIR(17u),
        VALUE_STATUS(20u),
        VALUE_ANGLE(21u),
        VALUE_ANGERROR(22u);

        companion object {
            fun forByteValue(value: UByte): DeviceMessageType? =
                values().find { it.byteValue == value }
        }
    }

    private fun decodeMessage(messageId: UByte, payload: UByteArray): DeviceMessage? {
        val messageType = DeviceMessageType.forByteValue(messageId)
        if (messageType == null) logger.error { "Unidentified message type received $messageId" }
        val payloadBuffer = ByteBuffer.wrap(payload.toByteArray())
        return when (messageType) {
            PARAM_CALIBRATE -> {
                val calibrateByte = payloadBuffer.readUByte()
                DeviceCalibrate(if (calibrateByte == (0xAAu).toUByte()) calibrated else uncalibrated)
            }
            PARAM_KP -> DeviceKp(PidParameter(payloadBuffer.readShort().toInt()))
            PARAM_KI -> DeviceKi(PidParameter(payloadBuffer.readShort().toInt()))
            PARAM_KD -> DeviceKd(PidParameter(payloadBuffer.readShort().toInt()))
            PARAM_CURRENT -> DeviceCurrent(Current(payloadBuffer.readUByte().toUInt()))
            PARAM_STEPSIZE -> DeviceStepSize(MicroSteps(payloadBuffer.readUByte().toUInt()))
            PARAM_ENDIR -> {
                val enableByte = payloadBuffer.readUByte()
                DeviceEnable(if (enableByte == (1u).toUByte()) enabled else disabled)
            }
            PARAM_MOTORDIR -> {
                val dirByte = payloadBuffer.readUByte()
                DeviceMotorDir(if (dirByte == (1u).toUByte()) forward else backward)
            }
            VALUE_STATUS -> readStatus(payload)
            VALUE_ANGLE -> DeviceAngle(Angle(payloadBuffer.readFloat().toDouble()))
            VALUE_ANGERROR -> DeviceAngleError(Angle(payloadBuffer.readFloat().toDouble()))
            null -> null
        }
    }

    private fun readStatus(payload: UByteArray): DeviceStatus {
        val statusByte = ByteBuffer.wrap(payload.toByteArray()).readUByte()
        val isEnabled = (statusByte and 0x01u).toUInt() == 0x01u
        val isCloseLoop = (statusByte and 0x02u).toUInt() == 0x02u
        return DeviceStatus(
            if (isEnabled) enabled else disabled,
            if (isCloseLoop) FeedbackMode.CLOSELOOP else FeedbackMode.OPENLOOP
        )
    }

    private suspend fun generateCrc(packet: InputStream): UByte = generateCrcRec(packet, 0xFFu)

    private tailrec suspend fun generateCrcRec(packet: InputStream, crc: UByte): UByte {
        val next = packet.readUByte() ?: return crc
        return generateCrcRec(packet, crcBits(next xor crc, 0))
    }

    private tailrec fun crcBits(crc: UByte, bit: Int): UByte {
        return when {
            bit == 8 -> crc
            (crc and 0x80u).toUInt() != 0u -> crcBits((crc shl 1) xor 0x31u, bit + 1)
            else -> crcBits(crc shl 1, bit + 1)
        }
    }

    companion object {
        suspend fun open(portName: String): TSDevice? {
            return try {
                val port = SerialPort.getCommPort(portName)
                val device = TSDevice(port.inputStream, port.outputStream)
                device.beginRead()
            } catch (e: SerialPortInvalidPortException) {
                null
            }
        }
    }

    private suspend fun beginRead(): TSDevice {
        withContext(Dispatchers.IO) {
            readIncomingSerial(serialIn, 0u)
        }
        return this
    }
}




