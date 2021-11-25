package com.github.ekohlwey.astrolabe.devices

import com.github.ekohlwey.astrolabe.*
import com.github.ekohlwey.astrolabe.Direction.backward
import com.github.ekohlwey.astrolabe.Direction.forward
import com.github.ekohlwey.astrolabe.HostMessage.*
import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.StreamAngleMode.silent
import com.github.ekohlwey.astrolabe.StreamAngleMode.stream
import com.github.ekohlwey.astrolabe.messages.*
import com.github.ekohlwey.astrolabe.messages.DeviceMessage.*
import com.github.ekohlwey.astrolabe.messages.EnableMode.disabled
import com.github.ekohlwey.astrolabe.messages.EnableMode.enabled
import com.github.ekohlwey.astrolabe.messages.FeedbackMode.CLOSELOOP
import com.github.ekohlwey.astrolabe.messages.FeedbackMode.OPENLOOP
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.*
import kotlin.concurrent.timerTask

class SimulatedDevice(private var initialState: DeviceState) : Device {
    private val internalMessages = Channel<DeviceMessage>()
    private val reportTimer = Timer("report angle", false)
    private val angleReportTask = timerTask { runBlocking { internalMessages.send(DeviceAngle(deviceState.angle)) } }
    override val messages = internalMessages.receiveAsFlow()
    private var deviceState = initialState

    override suspend fun writeHostMessages(hostMessages: Flow<HostMessage>) {
        logger.debug { "Processing host messages" }
        hostMessages.collect { hostMessage -> processMessage(hostMessage) }
    }

    override fun close() {
        reportTimer.cancel()
    }

    private suspend fun processMessage(hostMessage: HostMessage) {
        logger.trace { "Host message received: $hostMessage" }
        when (hostMessage) {
            is ReadValue -> {
                when (hostMessage) {
                    is ReadStatus -> internalMessages.send(DeviceStatus(deviceState.enableMode, deviceState.closeLoop))
                    is ReadAngle -> internalMessages.send(DeviceAngle(deviceState.angle))
                    is ReadAngleError -> internalMessages.send(DeviceAngleError(deviceState.error))
                    else -> PLUGIN_TODO
                }
            }
            is GetParameter -> {
                when (hostMessage) {
                    is GetCalibrate -> internalMessages.send(DeviceCalibrate(deviceState.calibrate))
                    is GetPidKp -> internalMessages.send(DeviceKp(deviceState.kp))
                    is GetPidKi -> internalMessages.send(DeviceKi(deviceState.ki))
                    is GetPidKd -> internalMessages.send(DeviceKd(deviceState.kd))
                    is GetCurrent -> internalMessages.send(DeviceCurrent(deviceState.current))
                    is GetStepSize -> internalMessages.send(DeviceStepSize(deviceState.stepSize))
                    is GetMotorDir -> internalMessages.send(DeviceMotorDir(deviceState.motorDir))
                    else -> PLUGIN_TODO
                }
            }
            is SetParameter -> {
                when (hostMessage) {
                    is SetCalibrate -> deviceState = deviceState.copy(calibrate = hostMessage.calibrate)
                    is SetPidKp -> deviceState = deviceState.copy(kp = hostMessage.pidKp)
                    is SetPidKi -> deviceState = deviceState.copy(ki = hostMessage.pidKi)
                    is SetPidKd -> deviceState = deviceState.copy(kd = hostMessage.pidKd)
                    is SetCurrent -> deviceState = deviceState.copy(current = hostMessage.current)
                    is SetStepSize -> deviceState = deviceState.copy(stepSize = hostMessage.steps)
                    is SetEnable -> deviceState = deviceState.copy(enableMode = hostMessage.enabled)
                    is SetMotorDir -> deviceState = deviceState.copy(motorDir = hostMessage.motorDir)
                    else -> PLUGIN_TODO
                }
            }
            is Command -> {
                when (hostMessage) {
                    is StreamAngle -> {
                        reportTimer.purge()
                        when (hostMessage.mode) {
                            stream -> reportTimer.schedule(angleReportTask, 10000)
                            silent -> {}
                        }
                    }
                    is StorageSave -> initialState = deviceState
                    is ModeEnable -> deviceState.enableMode = enabled
                    is ModeDisable -> deviceState.enableMode = disabled
                    is ModeCloseloop -> deviceState.closeLoop = CLOSELOOP
                    is ModeOpenloop -> deviceState.closeLoop = OPENLOOP
                    is JumpBootloader -> {
                        reportTimer.purge()
                        deviceState = initialState.copy(angle = deviceState.angle)
                    }
                    is Step -> deviceState.move(Steps(1u), hostMessage.direction)
                    is StepForwardCommand -> deviceState.move(hostMessage.step, forward)
                    is StepBackwardCommand -> deviceState.move(hostMessage.step, backward)
                    is MoveCommand -> deviceState.move(hostMessage.steps, deviceState.motorDir)
                    else -> PLUGIN_TODO
                }
            }
            else -> PLUGIN_TODO
        }
    }

    data class DeviceState(
        var enableMode: EnableMode = enabled,
        var desiredAngle: Angle = Angle(0.0),
        var current: Current = Current(800u),
        var angle: Angle = Angle(0.0),
        var kp: PidParameter = PidParameter(0),
        var kd: PidParameter = PidParameter(0),
        var ki: PidParameter = PidParameter(0),
        var closeLoop: FeedbackMode = CLOSELOOP,
        var calibrate: Calibrate = Calibrate.uncalibrated,
        var stepSize: MicroSteps = MicroSteps.default,
        var motorDir: Direction = forward
    ) {
        val error get() = desiredAngle - angle
        fun move(steps: Steps, direction: Direction) {
            val angleChange = this.angle + Angle(1.8) * (steps / this.stepSize) * direction
            this.angle = angleChange
            this.desiredAngle = angleChange
        }
    }

    companion object {
        val logger = KotlinLogging.logger { }
    }
}