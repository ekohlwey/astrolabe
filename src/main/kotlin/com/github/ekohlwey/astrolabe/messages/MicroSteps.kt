package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.messages.Stepish
import kotlin.math.log2


data class MicroSteps(override val amount: UInt) : Stepish {
    init {
        val logAmount = log2(amount.toDouble())
        // cast to int to truncate the remainder
        if (logAmount - logAmount.toInt().toDouble() != (0).toDouble()) {
            throw IllegalArgumentException("Microsteps must be a power of 2, instead got $amount")
        }
    }

    companion object {
        val default = MicroSteps(16u)
        fun findLastOrDefault(list: List<Any>): MicroSteps =
            list.findLast { e -> e is MicroSteps } as MicroSteps? ?: default
    }
}