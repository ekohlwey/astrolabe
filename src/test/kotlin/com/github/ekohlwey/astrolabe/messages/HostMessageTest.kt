package com.github.ekohlwey.astrolabe.messages

import com.github.ekohlwey.astrolabe.*
import com.github.ekohlwey.astrolabe.Direction.*
import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.SuccessfulParse
import com.github.ekohlwey.astrolabe.StreamAngleMode.*
import com.github.h0tk3y.betterParse.combinators.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe

class HostMessageTest : StringSpec({

    "Various get parameter invocations parse successfully" {
        table(
            headers("input", "output"),
            row("get param calibrate", GetCalibrate),
            row("get param kp", GetPidKp),
            row("get param ki", GetPidKi),
            row("get param kd", GetPidKd),
            row("get param current", GetCurrent),
            row("get param step_size", GetStepSize),
            row("get param enable", GetEnabled),
            row("get param direction", GetMotorDir)
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

    "Various get value invocations parse successfully" {
        table(
            headers("input", "output"),
            row("get value status", ReadStatus),
            row("get value angle", ReadAngle),
            row("get value angle_error", ReadAngleError)
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

    "Various set param invocations parse successfully" {
        table(
            headers("input", "output"),
            row("set param calibrate = true", SetCalibrate(Calibrate.calibrated)),
            row("set param calibrate = false", SetCalibrate(Calibrate.uncalibrated)),
            row("set param kp = 12", SetPidKp(PidParameter(12))),
            row("set param ki = 13", SetPidKi(PidParameter(13))),
            row("set param kd = 14", SetPidKd(PidParameter(14))),
            row("set param current = 800", SetCurrent(Current(800u))),
            row("set param step_size = 16", SetStepSize(MicroSteps(16u))),
            row("set param enable = false", SetEnable(EnableMode.disabled)),
            row("set param enable = true", SetEnable(EnableMode.enabled)),
            row("set param direction = forward", SetMotorDir(forward)),
            row("set param direction = backward", SetMotorDir(backward))
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

    "Various set mode invocations parse successfully" {
        table(
            headers("input", "output"),
            row("set mode enabled", ModeEnable),
            row("set mode disabled", ModeDisable),
            row("set mode stream", StreamAngle(stream)),
            row("set mode silent", StreamAngle(silent)),
            row("set mode open_loop", ModeOpenloop),
            row("set mode closed_loop", ModeCloseloop),
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

    "Parses movement commands" {
        table(
            headers("input", "output"),
            row("step", Step(forward)),
            row("step forward", Step(forward)),
            row("step backward", Step(backward)),
            row("step forward current = 10", StepForwardCommand(Steps.default, Current(10u))),
            row("step forward steps = 10", StepForwardCommand(Steps(10u), Current.default)),
            row("step forward steps = 100 current = 100", StepForwardCommand(Steps(100u), Current(100u))),
            row("step forward steps = 100 steps = 200", StepForwardCommand(Steps(200u), Current.default)),
            row("step backward current = 10", StepBackwardCommand(Steps.default, Current(10u))),
            row("step backward steps = 10", StepBackwardCommand(Steps(10u), Current.default)),
            row("step backward steps = 100 current = 100", StepBackwardCommand(Steps(100u), Current(100u))),
            row("step backward steps = 100 steps = 200", StepBackwardCommand(Steps(200u), Current.default)),
            row("move steps = 10 forward", MoveCommand(Steps(10u), forward)),
            row("move backward", MoveCommand(Steps.default, backward)),
            row("move", MoveCommand(Steps.default, Direction.default))
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

    "Parses non-movement commands" {
        table(
            headers("input", "output"),
            row("restart", JumpBootloader),
            row("save", StorageSave)
        ).forAll { input, output ->
            HostMessage.tryParseToEnd(input) shouldBe SuccessfulParse(output)
        }
    }

})