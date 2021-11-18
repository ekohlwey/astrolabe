package com.github.ekohlwey.astrolabe

enum class Direction {
    forward,
    backward;

    companion object {
        fun findLastOrDefault(list: List<Any>) = list.findLast { it is Direction } as Direction? ?: forward
    }
}