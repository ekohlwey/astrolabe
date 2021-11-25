package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.Direction.*
import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.messages.Calibrate
import com.github.ekohlwey.astrolabe.messages.Calibrate.calibrated
import com.github.ekohlwey.astrolabe.messages.Calibrate.uncalibrated
import com.github.ekohlwey.astrolabe.messages.EnableMode
import com.github.ekohlwey.astrolabe.messages.EnableMode.disabled
import com.github.ekohlwey.astrolabe.messages.EnableMode.enabled
import com.github.ekohlwey.astrolabe.messages.PidParameter
import com.github.ekohlwey.astrolabe.messages.Steps
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.*
import java.io.PrintStream


sealed class HostMessage {

    sealed class HostMessageParse

    data class HostMessageParseError(private val errorResult: ErrorResult) : HostMessageParse() {
        fun printHelp(consoleOut: PrintStream) {
            when (errorResult) {
//                is BadGetName -> consoleOut.printBadGetHelp(errorResult)
                else -> {
                    consoleOut.appendLine(errorResult.toString())
                }
            }
            consoleOut.flush()
        }

    }

    data class SuccessfulParse(val hostMessage: HostMessage) : HostMessageParse()

    companion object {
        private fun literalTokenWithSameName(name: String): Token = literalToken(name, name)
        fun tryParseToEnd(input: CharSequence): HostMessageParse {
            return when (val parseResult = grammar.tryParseToEnd(input.toString())) {
                is ErrorResult -> HostMessageParseError(parseResult)
                is Parsed -> SuccessfulParse(parseResult.value)
            }
        }

        private class MapErrorCombinator<R>(val delegate: Parser<R>, val mapper: (ErrorResult) -> ErrorResult) :
            Parser<R> {
            override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<R> =
                when (val parseResult = delegate.tryParse(tokens, fromPosition)) {
                    is Parsed -> parseResult
                    is ErrorResult -> mapper.invoke(parseResult)
                }
        }

        private infix fun <R> Parser<R>.mapError(mapper: (ErrorResult) -> ErrorResult): Parser<R> =
            MapErrorCombinator(this@mapError, mapper)

        private data class ExpectedToken(val token: Token, val source: ErrorResult) : ErrorResult()

        private operator fun Token.unaryPlus(): Parser<TokenMatch> =
            this.mapError { e: ErrorResult -> ExpectedToken(this@unaryPlus, e) }

        private data class OneExpected(val failedOptions: List<ExpectedToken>) : ErrorResult()

        private operator fun <T> Parser<T>.not(): Parser<T> = this.mapError { e ->
            if (e is AlternativesFailure && e.errors.all { it is ExpectedToken })
                OneExpected(e.errors.map { it as ExpectedToken })
            else if (e is AlternativesFailure && e.errors.any { it is OneExpected })
                e.errors.find { it is OneExpected }!!
            else
                e
        }

        val grammar = object : Grammar<HostMessage>() {
            val paramCal by literalTokenWithSameName("calibrate")
            val paramKp by literalTokenWithSameName("kp")
            val paramKi by literalTokenWithSameName("ki")
            val paramKd by literalTokenWithSameName("kd")
            val paramCurrent by literalTokenWithSameName("current")
            val paramStepSize by literalTokenWithSameName("step_size")
            val modeEnable by literalTokenWithSameName("enabled")
            val paramEnDir by literalTokenWithSameName("enable")
            val paramMotorDir by literalTokenWithSameName("direction")
            val eq by literalTokenWithSameName("=")
            val nint by regexToken("<signed_int>", "-\\d+")
            val int by regexToken("<unsigned_int>", "\\d+")
            val set by literalTokenWithSameName("set")
            val get by literalTokenWithSameName("get")
            val forwardDir by literalTokenWithSameName(forward.name)
            val backwardDir by literalTokenWithSameName(backward.name)
            val stepForward by literalTokenWithSameName("step_forward")
            val stepBack by literalTokenWithSameName("step_backward")
            val current by literalTokenWithSameName("current")
            val step by literalTokenWithSameName("step")
            val move by literalTokenWithSameName("move")
            val storageSave by literalTokenWithSameName("save")
            val jumpBootloader by literalTokenWithSameName("restart")
            val bTrue by literalTokenWithSameName("true")
            val bFalse by literalTokenWithSameName("false")
            val param by literalTokenWithSameName("param")
            val value by literalTokenWithSameName("value")
            val status by literalTokenWithSameName("status")
            val angError by literalTokenWithSameName("angle_error")
            val angle by literalTokenWithSameName("angle")
            val mode by literalTokenWithSameName("mode")
            val modeDisable by literalTokenWithSameName("disabled")
            val modeCloseloop by literalTokenWithSameName("closed_loop")
            val modeOpenloop by literalTokenWithSameName("open_loop")
            val modeStreamAngle by literalTokenWithSameName("stream")
            val modeNoStreamAngle by literalTokenWithSameName("silent")
            @Suppress("unused") val ws by regexToken("<space>", "\\s*", ignore = true)

            // begin values
            // end values
            // end value section

            // begin mode section

            // begin modes
            // end modes
            // end mode section

            // start literals
            val unsignedInt by +int map { it.text.toInt().toUInt() }
            val signedInt by +nint or +int map { it.text.toInt() }
            val direction by !(+forwardDir or +backwardDir) map { valueOf(it.text) }
            // end literals

            // assignments
            val calAssignment by -+eq and !(
                    (+bTrue map { calibrated }) or
                            (+bFalse map { uncalibrated })
                    )
            val pidAssignment by -+eq and signedInt map { PidParameter(it) }
            val currentAssignment by -+eq and unsignedInt map { Current(it) }
            val stepAssignment by -+eq and unsignedInt map { MicroSteps(it) }
            val enableAssignment by -+eq and !(
                    (+bTrue map { enabled }) or
                            (+bFalse map { disabled })
                    )
            val dirAssignment by -+eq and !direction
            // end assignments

            // start commands
            // start "setters"
            val setParam by -param and !(
                    (-+paramCal and calAssignment map { SetCalibrate(it) }) or
                            (-+paramKd and pidAssignment map { SetPidKd(it) }) or
                            (-+paramKi and pidAssignment map { SetPidKi(it) }) or
                            (-+paramKp and pidAssignment map { SetPidKp(it) }) or
                            (-+paramCurrent and currentAssignment map { SetCurrent(it) }) or
                            (-+paramStepSize and stepAssignment map { SetStepSize(it) }) or
                            (-+paramEnDir and enableAssignment map { SetEnable(it) }) or
                            (-+paramMotorDir and dirAssignment map { SetMotorDir(it) })
                    )
            val setMode by -+mode and !(
                    (+modeEnable map { ModeEnable }) or
                            (+modeDisable map { ModeDisable }) or
                            (+modeCloseloop map { ModeCloseloop }) or
                            (+modeOpenloop map { ModeOpenloop })
                    )
            val setStreamAngle by -+mode and !(
                    (+modeStreamAngle map { StreamAngleMode.stream }) or
                            (+modeNoStreamAngle map { StreamAngleMode.silent })
                    ) map { StreamAngle(it) }
            val setter by -+set and !(setParam or setMode or setStreamAngle)
            // end setters

            // start getters
            val getParamCandidate = (+paramCal map { GetCalibrate }) or
                    (+paramKp map { GetPidKp }) or
                    (+paramKi map { GetPidKi }) or
                    (+paramKd map { GetPidKd }) or
                    (+paramCurrent map { GetCurrent }) or
                    (+paramStepSize map { GetStepSize }) or
                    (+paramEnDir map { GetEnabled }) or
                    (+paramMotorDir map { GetMotorDir })

            val getParam by -+param and !getParamCandidate

            val getValueCandidate = (+status map { ReadStatus }) or
                    (+angle map { ReadAngle }) or
                    (+angError map { ReadAngleError })

            val getValue by -+value and !getValueCandidate

            val getter by -+get and !(getValue or getParam)

            // start movements
            val doStep by -+step and optional(direction) map { Step(it ?: forward) }
            val numSteps by -+step and unsignedInt
            val currentAmount by -+current and unsignedInt
            val dirStepArg by !(numSteps or currentAmount)
            val doStepForward by -+stepForward and zeroOrMore(dirStepArg) map {
                StepForwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val doStepBackward by -+stepBack and zeroOrMore(dirStepArg) map {
                StepBackwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val moveArg by !(numSteps or direction)
            val doMove by -+move and zeroOrMore(moveArg) map {
                MoveCommand(Steps.findLastOrDefault(it), Direction.findLastOrDefault(it))
            }
            // end movements

            val doSave by +storageSave map { StorageSave }
            val doJumpBootloader by +jumpBootloader map { JumpBootloader }

            val command by !(setMode or
                    setStreamAngle or
                    doStep or
                    doStepForward or
                    doStepBackward or
                    doMove or
                    doSave or
                    doJumpBootloader)

            override val rootParser by !(setter or
                    getter or
                    command)

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

        data class StepForwardCommand(override val step: Steps, override val current: Current) :
            Command(), StepCommandDetails

        data class StepBackwardCommand(override val step: Steps, override val current: Current) :
            Command(),
            StepCommandDetails

        data class MoveCommand(val steps: Steps, val direction: Direction) : Command()
    }


}

