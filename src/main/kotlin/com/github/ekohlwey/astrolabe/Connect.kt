package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.Connect.PossibleDevice.simulated
import com.github.ekohlwey.astrolabe.devices.Console
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice.DeviceState
import kotlinx.coroutines.*
import mu.KotlinLogging
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "connect",
    description = ["Connect to a device"],
    version = ["1.0.0"],
    mixinStandardHelpOptions = true
)
class Connect : Callable<Int> {

    enum class PossibleDevice {
        truestep,
        simulated
    }

    @CommandLine.Option(
        description = ["The device type to use."],
        defaultValue = "truestep",
        names = ["-d", "--device"]
    )
    var device: PossibleDevice? = null

    override fun call(): Int = runBlocking { doConnect() }


    private suspend fun doConnect(): Int {
        if (device == simulated) {
            return SimulatedDevice(DeviceState()).use { device -> connectToConsole(device) }
        } else {
            TODO("Unimplemented")
        }
    }

    private suspend fun connectToConsole(device: SimulatedDevice): Int {
        return Console(System.`in`, System.out).use { console ->
            withContext(Dispatchers.Default) {
                kotlin.runCatching {
                    listOf(
                        launch {
                            console.startReading()
                            logger.debug { "Done reading console, closing device" }
                            device.close()
                            logger.debug { "Device closed" }
                        },
                        launch {
                            console.writeDeviceMessages(device.messages)
                            logger.debug { "Done writing device messages" }
                        },
                        launch {
                            device.writeHostMessages(console.messages)
                            logger.debug { "Done writing host messages" }
                        }
                    ).joinAll()
                }.onFailure { logger.error(it) { "Error running console" } }.fold({ 0 }, { 1 })
            }
        }
    }

    companion object {
        val logger = KotlinLogging.logger { }
    }

}