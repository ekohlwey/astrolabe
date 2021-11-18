package com.github.ekohlwey.astrolabe

data class Current(val amount: UInt) {
    companion object {
        private val default = Current(800u)
        fun findLastOrDefault(list: List<Any>): Current =
            list.findLast { it is Current } as Current? ?: default
    }
}