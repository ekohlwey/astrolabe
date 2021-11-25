package com.github.ekohlwey.astrolabe.messages

import com.github.ekohlwey.astrolabe.MicroSteps

data class Steps (override val amount: UInt): Stepish {

    companion object {
        val default = Steps(1u)
        fun findLastOrDefault(list: List<Any>): Steps =
            list.findLast { e -> e is Steps } as Steps? ?: default
    }
}