package com.github.ekohlwey.astrolabe.messages

interface Stepish {
    val amount: UInt
    operator fun div(other: Stepish): Double = this.amount.toDouble() / other.amount.toDouble()
}