package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.PossibleDevice.*
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice.DeviceState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "connect",
    description = ["Connect to a device"],
    version = ["1.0.0"],
    mixinStandardHelpOptions = true
)
class ConnectDevice : Callable<Int> {

    @CommandLine.Option(
        description = ["The device type to use."],
        defaultValue = "truestep",
        names = ["-d", "--device"]
    )
    var device: PossibleDevice? = null

    override fun call(): Int = runBlocking { connectDevice() }

    suspend fun connectDevice(): Int {
        if (device == simulated) {
            SimulatedDevice(DeviceState()).use { device ->
                Console.open(System.`in`, System.out).use { console ->
                    console.writeDeviceMessages(device.messages)
                    device.writeHostMessages(console.messages)
                }
            }
            return 0
        } else {
            TODO("Unimplemented")
        }
    }
}