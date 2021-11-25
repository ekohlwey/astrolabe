package com.github.ekohlwey.astrolabe

enum class Direction {
    forward,
    backward;

    companion object {
        val default = forward
        fun findLastOrDefault(list: List<Any>) = list.findLast { it is Direction } as Direction? ?: default
    }
}