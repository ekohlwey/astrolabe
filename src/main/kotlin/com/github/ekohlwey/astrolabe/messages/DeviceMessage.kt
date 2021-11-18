package com.github.ekohlwey.astrolabe.messages

import com.github.ekohlwey.astrolabe.Current
import com.github.ekohlwey.astrolabe.Direction
import com.github.ekohlwey.astrolabe.MicroSteps

sealed class DeviceMessage {
    data class DeviceCalibrate(val calibrate: Calibrate) : DeviceMessage()
    data class DeviceKp(val kp:PidParameter): DeviceMessage()
    data class DeviceKi(val ki:PidParameter):DeviceMessage()
    data class DeviceKd(val kd:PidParameter):DeviceMessage()
    data class DeviceCurrent(val currentMillis:Current):DeviceMessage()
    data class DeviceStepSize(val size: MicroSteps):DeviceMessage()
    data class DeviceEnable(val enabled: EnableMode):DeviceMessage()
    data class DeviceMotorDir(val motorDir:Direction):DeviceMessage()
    data class DeviceAngle(val angle:Angle):DeviceMessage()
    data class DeviceAngleError(val angleError:Angle):DeviceMessage()

    data class DeviceStatus(val enabled: EnableMode, val mode: FeedbackMode) : DeviceMessage()
    companion object
}



