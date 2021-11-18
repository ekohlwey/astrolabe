package com.github.ekohlwey.astrolabe

import java.util.concurrent.Callable
import kotlin.Throws
import java.util.Arrays
import com.fazecast.jSerialComm.SerialPort
import mu.KotlinLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.lang.Exception
import java.lang.RuntimeException

@CommandLine.Command(
    name = "listports",
    description = ["List serial ports that can be attached to"],
    version = ["1.0.0"],
    mixinStandardHelpOptions = true
)
class ListPorts : Callable<Int> {
    private val logger = KotlinLogging.logger {}
    override fun call(): Int {
        return try {
            SerialPort.getCommPorts()
                .mapNotNull { p -> p.descriptivePortName }
                .forEach(System.out::println)
            0
        } catch (e: RuntimeException) {
            logger.error(e) { "Error listing ports" }
            1
        }
    }
}