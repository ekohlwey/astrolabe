package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.Connect.PossibleDevice.*
import com.github.ekohlwey.astrolabe.devices.Console
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice
import com.github.ekohlwey.astrolabe.devices.SimulatedDevice.DeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import picocli.CommandLine
import java.io.InputStreamReader
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
            SimulatedDevice(DeviceState()).use { device ->
                Console(InputStreamReader(System.`in`), System.out).use { console ->
                    withContext(Dispatchers.Default){
                        launch {console.startReading()}
                        launch {console.writeDeviceMessages(device.messages)}
                        launch {device.writeHostMessages(console.messages)}
                    }
                }
            }
            return 0
        } else {
            TODO("Unimplemented")
        }
    }

}