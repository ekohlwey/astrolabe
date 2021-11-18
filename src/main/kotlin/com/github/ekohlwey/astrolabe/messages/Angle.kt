package com.github.ekohlwey.astrolabe.messages

import com.github.ekohlwey.astrolabe.Direction

data class Angle(val measurement: Double) {
    operator fun minus(other: Angle): Angle = Angle(this.measurement - other.measurement)
    operator fun plus(other: Angle): Angle = Angle(this.measurement + other.measurement)
    operator fun times(multiplicand: Double): Angle = Angle(this.measurement * multiplicand)
    operator fun times(dir: Direction): Angle = Angle(
        this.measurement * when (dir) {
            Direction.forward -> 1
            Direction.backward -> -1
        }
    )
}