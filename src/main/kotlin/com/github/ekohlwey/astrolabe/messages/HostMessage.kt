package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.Direction.*
import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.messages.Calibrate
import com.github.ekohlwey.astrolabe.messages.Calibrate.*
import com.github.ekohlwey.astrolabe.messages.EnableMode
import com.github.ekohlwey.astrolabe.messages.EnableMode.*
import com.github.ekohlwey.astrolabe.messages.PidParameter
import com.github.ekohlwey.astrolabe.messages.Steps
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.ParseResult


sealed class HostMessage {

    companion object {
        fun tryParseToEnd(input: CharSequence): ParseResult<HostMessage> = grammar.tryParseToEnd(input.toString())
        private val grammar = object : Grammar<HostMessage>() {
            val ws by regexToken("\\s*", ignore = true)
            val eq by literalToken("=")
            val nint by regexToken("-\\d+")
            val int by regexToken("\\d+")
            val float by regexToken("-?\\d+\\.\\d+")
            val set by literalToken("set")
            val get by literalToken("get")
            val step by literalToken("step")
            val forwardDir by literalToken("forward")
            val backwardDir by literalToken("backward")
            val stepForward by literalToken("step_forward")
            val steps by literalToken("steps")
            val current by literalToken("current")
            val stepBack by literalToken("step_backward")
            val move by literalToken("move")
            val storageSave by literalToken("save")
            val jumpBootloader by literalToken("restart")
            val bTrue by literalToken("true")
            val bFalse by literalToken("false")

            // begin param section
            val param by literalToken("param")

            // begin param names
            val paramCal by literalToken("calibrate")
            val paramKp by literalToken("kp")
            val paramKi by literalToken("ki")
            val paramKd by literalToken("kd")
            val paramCurrent by literalToken("current")
            val paramStepSize by literalToken("stepsize")
            val paramEnDir by literalToken("enable")
            val paramMotorDir by literalToken("dir")
            // end param names
            // end param section

            // begin value section
            val value by literalToken("value")

            // begin values
            val status by literalToken("status")
            val angle by literalToken("angle")
            val angError by literalToken("angle_error")
            // end values
            // end value section

            // begin mode section
            val mode by literalToken("mode")

            // begin modes
            val modeEnable by literalToken("enable")
            val modeDisable by literalToken("disable")
            val modeCloseloop by literalToken("closeloop")
            val modeOpenloop by literalToken("openloop")
            val modeStreamAngle by literalToken("stream_angle")
            val modeNoStreamAngle by literalToken("no_stream_angle")
            // end modes
            // end mode section

            // start literals
            val unsignedInt by int map { it.text.toInt().toUInt() }
            val signedInt by nint or int map { it.text.toInt() }
            val direction by forwardDir or backwardDir map { Direction.valueOf(it.text) }
            // end literals

            // assignments
            val calAssignment by -eq and
                    bTrue map { calibrated } or
                    bFalse map { uncalibrated }
            val pidAssignment by -eq and signedInt map { PidParameter(it) }
            val currentAssignment by -eq and unsignedInt map { Current(it) }
            val stepAssignment by -eq and unsignedInt map { MicroSteps(it) }
            val enableAssignment by -eq and
                    bTrue map { enabled } or
                    bFalse map { disabled }
            val dirAssignment by -eq and direction
            // end assignments

            // start commands
            // start "setters"
            val setParam by -set and -param and
                    (-paramCal and calAssignment map { SetCalibrate(it) }) or
                    (-paramKd and pidAssignment map { SetPidKd(it) }) or
                    (-paramKi and pidAssignment map { SetPidKi(it) }) or
                    (-paramKp and pidAssignment map { SetPidKp(it) }) or
                    (-paramCurrent and currentAssignment map { SetCurrent(it) }) or
                    (-paramStepSize and stepAssignment map { SetStepSize(it) }) or
                    (-paramEnDir and enableAssignment map { SetEnable(it) }) or
                    (-paramMotorDir and dirAssignment map { SetMotorDir(it) })

            val setMode by -set and -mode and (
                    modeEnable map { ModeEnable } or
                            modeDisable map { ModeDisable } or
                            modeCloseloop map { ModeCloseloop } or
                            modeOpenloop map { ModeOpenloop }
                    )
            val setStreamAngle by -set and -mode and (
                    modeStreamAngle map { StreamAngleMode.STREAM } or
                            modeNoStreamAngle map { StreamAngleMode.SILENT }
                    ) map { StreamAngle(it) }
            // end setters

            // start getters
            val getParam by -get and -param and (
                    paramCal map { GetCalibrate } or
                            paramKp map { GetPidKp } or
                            paramKi map { GetPidKi } or
                            paramKd map { GetPidKd } or
                            paramCurrent map { GetCurrent } or
                            paramStepSize map { GetStepSize } or
                            paramEnDir map { GetEnabled } or
                            paramMotorDir map { GetMotorDir }
                    )
            val getValue by -get and -value and (
                    status map { ReadStatus } or
                            angle map { ReadAngle } or
                            angError map { ReadAngleError }
                    )
            // end getters

            // start movements
            val doStep by -step and optional(direction) map { Step(it ?: forward) }
            val numSteps by -step and unsignedInt
            val currentAmount by -current and unsignedInt
            val dirStepArg by numSteps or currentAmount
            val doStepForward by -stepForward and zeroOrMore(dirStepArg) map {
                StepForwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val doStepBackward by -stepBack and zeroOrMore(dirStepArg) map {
                StepBackwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val moveArg by numSteps or direction
            val doMove by -move and zeroOrMore(moveArg) map {
                MoveCommand(Steps.findLastOrDefault(it), Direction.findLastOrDefault(it))
            }
            // end movements

            val doSave by storageSave map { StorageSave }
            val doJumpBootloader by jumpBootloader map { JumpBootloader }

            val command by setMode or
                    setStreamAngle or
                    doStep or
                    doStepForward or
                    doStepBackward or
                    doMove or
                    doSave or
                    doJumpBootloader

            override val rootParser by setParam or
                    getParam or
                    getValue or
                    command
        }
    }

    sealed class ReadValue : HostMessage() {
        object ReadStatus : ReadValue()
        object ReadAngle : ReadValue()
        object ReadAngleError : ReadValue()
    }

    sealed class GetParameter : HostMessage() {
        object GetCalibrate : GetParameter()
        object GetPidKp : GetParameter()
        object GetPidKi : GetParameter()
        object GetPidKd : GetParameter()
        object GetCurrent : GetParameter()
        object GetStepSize : GetParameter()
        object GetEnabled : GetParameter()
        object GetMotorDir : GetParameter()
    }

    sealed class SetParameter : HostMessage() {
        data class SetCalibrate(val calibrate: Calibrate) : SetParameter()
        data class SetPidKp(val pidKp: PidParameter) : SetParameter()
        data class SetPidKi(val pidKi: PidParameter) : SetParameter()
        data class SetPidKd(val pidKd: PidParameter) : SetParameter()
        data class SetCurrent(val current: Current) : SetParameter()
        data class SetStepSize(val steps: MicroSteps) : SetParameter()
        data class SetEnable(val enabled: EnableMode) : SetParameter()
        data class SetMotorDir(val motorDir: Direction) : SetParameter()
    }


    sealed class Command : HostMessage() {
        data class StreamAngle(val mode: StreamAngleMode) : Command()
        object StorageSave : Command()
        object ModeEnable : Command()
        object ModeDisable : Command()
        object ModeCloseloop : Command()
        object ModeOpenloop : Command()
        object JumpBootloader : Command()
        class Step(val direction: Direction) : Command()
        interface StepCommandDetails {
            val step: Steps
            val current: Current
        }

        class StepForwardCommand(override val step: Steps, override val current: Current) :
            Command(), StepCommandDetails

        class StepBackwardCommand(override val step: Steps, override val current: Current) :
            Command(),
            StepCommandDetails

        class MoveCommand(val steps: Steps, val direction: Direction) : Command()
    }

}








