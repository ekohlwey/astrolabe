package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.HostMessage.Command.*
import com.github.ekohlwey.astrolabe.HostMessage.GetParameter.*
import com.github.ekohlwey.astrolabe.HostMessage.ReadValue.*
import com.github.ekohlwey.astrolabe.HostMessage.SetParameter.*
import com.github.ekohlwey.astrolabe.messages.Calibrate
import com.github.ekohlwey.astrolabe.messages.EnableMode
import com.github.ekohlwey.astrolabe.messages.PidParameter
import com.github.ekohlwey.astrolabe.messages.Steps
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.tryParseToEnd
import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.*


typealias ErrorMapper = (ErrorResult) -> ErrorResult

sealed class HostMessage {

    sealed class HostMessageParse

    data class HostMessageParseError(private val errorResult: ErrorResult) : HostMessageParse()

    data class SuccessfulParse(val hostMessage: HostMessage) : HostMessageParse()

    companion object {
        private fun literalTokenWithSameName(name: String): Token = literalToken(name, name)
        fun tryParseToEnd(input: CharSequence): HostMessageParse {
            return when (val parseResult = grammar.tryParseToEnd(input.toString())) {
                is ErrorResult -> HostMessageParseError(parseResult)
                is Parsed -> SuccessfulParse(parseResult.value)
            }
        }


        private class MapErrorCombinator<R>(val delegate: Parser<R>, val mapper: ErrorMapper) :
            Parser<R> {
            override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<R> =
                when (val parseResult = delegate.tryParse(tokens, fromPosition)) {
                    is Parsed -> parseResult
                    is ErrorResult -> mapper.invoke(parseResult)
                }
        }

        private infix fun <R> Parser<R>.mapError(mapper: ErrorMapper): Parser<R> =
            MapErrorCombinator(this@mapError, mapper)

        internal data class ExpectedToken(val token: Token, val source: ErrorResult) : ErrorResult() {
            internal val mismatchText: String?
                get() = (this.source as? NoMatchingToken)?.tokenMismatch?.text
                    ?: (this.source as? MismatchedToken)?.found?.text
        }


        private val Token.completionCandidates: List<String>
            get() = (this as? LiteralToken)?.let { listOf(it.text) } ?: emptyList()

        internal val ErrorResult.completionCandidates: List<String>
            get() = when (this) {
                is ExpectedToken -> {
                    if (this.source is UnexpectedEof || this.token.startsWith(this.mismatchText)) {
                        this.token.completionCandidates
                    } else {
                        emptyList()
                    }
                }
                is OneExpected -> this.failedOptions.flatMap { it.completionCandidates }
                is AlternativesFailure -> this.errors.flatMap { it.completionCandidates }
                is UnparsedRemainder -> emptyList()
                is NoMatchingToken -> emptyList()
                is MismatchedToken -> if (this.expected.startsWith(this.found.text)) {
                    this.expected.completionCandidates
                } else {
                    emptyList()
                }
                is UnexpectedEof -> emptyList()
                else -> emptyList()
            }


        private fun Token.startsWith(candidate: String?): Boolean {
            return (this as? LiteralToken)?.text?.startsWith(candidate ?: return false) ?: false
        }

//        private operator fun <T> Parser<T>.unaryPlus(): Parser<T> =
//            this.mapError {
//
//            }

        private operator fun Token.unaryPlus(): Parser<TokenMatch> =
            this.mapError { ExpectedToken(this@unaryPlus, it) }


        internal data class OneExpected(val failedOptions: List<ExpectedToken>) : ErrorResult()

        private operator fun <T> Parser<T>.not(): Parser<T> = this.mapError { e ->
            if (e is AlternativesFailure) {
                if(e.errors.all { it is ExpectedToken }) {
                    OneExpected(
                        e.errors.map { it as ExpectedToken }
                    )
                } else if (e.errors.any { it is OneExpected }) {
                    e.errors.find { it is OneExpected }!!
                } else {
                    e
                }
            }  else {
                e
            }
        }

        val grammar = object : Grammar<HostMessage>() {
            val calibrate by literalTokenWithSameName("calibrate")
            val kp by literalTokenWithSameName("kp")
            val ki by literalTokenWithSameName("ki")
            val kd by literalTokenWithSameName("kd")
            val current by literalTokenWithSameName("current")
            val stepSize by literalTokenWithSameName("step_size")
            val enabled by literalTokenWithSameName("enabled")
            val enable by literalTokenWithSameName("enable")
            val direction by literalTokenWithSameName("direction")
            val eq by literalTokenWithSameName("=")
            val nint by regexToken("<signed_int>", "-\\d+")
            val int by regexToken("<unsigned_int>", "\\d+")
            val set by literalTokenWithSameName("set")
            val get by literalTokenWithSameName("get")
            val forwardDir by literalTokenWithSameName(Direction.forward.name)
            val backwardDir by literalTokenWithSameName(Direction.backward.name)
            val steps by literalTokenWithSameName("steps")
            val step by literalTokenWithSameName("step")
            val move by literalTokenWithSameName("move")
            val save by literalTokenWithSameName("save")
            val restart by literalTokenWithSameName("restart")
            val bTrue by literalTokenWithSameName("true")
            val bFalse by literalTokenWithSameName("false")
            val param by literalTokenWithSameName("param")
            val value by literalTokenWithSameName("value")
            val status by literalTokenWithSameName("status")
            val angleError by literalTokenWithSameName("angle_error")
            val angle by literalTokenWithSameName("angle")
            val mode by literalTokenWithSameName("mode")
            val disabled by literalTokenWithSameName("disabled")
            val closedLoop by literalTokenWithSameName("closed_loop")
            val openLoop by literalTokenWithSameName("open_loop")
            val stream by literalTokenWithSameName("stream")
            val silent by literalTokenWithSameName("silent")

            @Suppress("unused")
            val ws by regexToken("<space>", "\\s*", ignore = true)

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
            val directionValue by !(+forwardDir or +backwardDir) map { Direction.valueOf(it.text) }
            // end literals

            // assignments
            val calAssignment by -+eq and !(
                    (+bTrue map { Calibrate.calibrated }) or
                            (+bFalse map { Calibrate.uncalibrated })
                    )
            val pidAssignment by -+eq and signedInt map { PidParameter(it) }
            val currentAssignment by -+eq and unsignedInt map { Current(it) }
            val stepAssignment by -+eq and unsignedInt map { MicroSteps(it) }
            val enableAssignment by -+eq and !(
                    (+bTrue map { EnableMode.enabled }) or
                            (+bFalse map { EnableMode.disabled })
                    )
            val dirAssignment by -+eq and !directionValue
            // end assignments

            // start commands
            // start "setters"
            val setParamCandidate by !(-+calibrate and calAssignment map { SetCalibrate(it) }) or
                    !(-+kd and pidAssignment map { SetPidKd(it) }) or
                    !(-+ki and pidAssignment map { SetPidKi(it) }) or
                    !(-+kp and pidAssignment map { SetPidKp(it) }) or
                    !(-+current and currentAssignment map { SetCurrent(it) }) or
                    !(-+stepSize and stepAssignment map { SetStepSize(it) }) or
                    !(-+enable and enableAssignment map { SetEnable(it) }) or
                    !(-+direction and dirAssignment map { SetMotorDir(it) })
            val setParam by -+param and !setParamCandidate
            val setModeCandidate = (+enabled map { ModeEnable }) or
                    (+disabled map { ModeDisable }) or
                    (+closedLoop map { ModeCloseloop }) or
                    (+openLoop map { ModeOpenloop }) or
                    (+stream map { StreamAngle(StreamAngleMode.stream) }) or
                    (+silent map { StreamAngle(StreamAngleMode.silent) })
            val setMode by -+mode and !setModeCandidate
            val setter by -+set and !(setParam or setMode)
            // end setters

            // start getters
            val getParamCandidate = (+calibrate map { GetCalibrate }) or
                    (+kp map { GetPidKp }) or
                    (+ki map { GetPidKi }) or
                    (+kd map { GetPidKd }) or
                    (+current map { GetCurrent }) or
                    (+stepSize map { GetStepSize }) or
                    (+enable map { GetEnabled }) or
                    (+direction map { GetMotorDir })

            val getParam by -+param and !getParamCandidate

            val getValueCandidate = (+status map { ReadStatus }) or
                    (+angle map { ReadAngle }) or
                    (+angleError map { ReadAngleError })

            val getValue by -+value and !getValueCandidate

            val getter by -+get and !(getValue or getParam)

            // start movements
            val numSteps by -+steps and -+eq and unsignedInt map { Steps(it) }
            val currentAmount by -+current and -+eq and unsignedInt map { Current(it) }
            val dirStepArg by !(numSteps or currentAmount)
            val doStepForward by -+forwardDir and oneOrMore(dirStepArg) map {
                StepForwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val doStepBackward by -+backwardDir and oneOrMore(dirStepArg) map {
                StepBackwardCommand(Steps.findLastOrDefault(it), Current.findLastOrDefault(it))
            }
            val plainStep by optional(directionValue) map { Step(it ?: Direction.forward) }
            val doStep by -+step and !(doStepForward or doStepBackward or plainStep)
            val moveArg by !(numSteps or directionValue)
            val doMove by -+move and zeroOrMore(moveArg) map {
                MoveCommand(Steps.findLastOrDefault(it), Direction.findLastOrDefault(it))
            }
            // end movements

            val doSave by +save map { StorageSave }
            val doJumpBootloader by +restart map { JumpBootloader }

            override val rootParser by !(setter or
                    getter or
                    doStep or
                    doMove or
                    doSave or
                    doJumpBootloader)

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
        data class Step(val direction: Direction) : Command()
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

