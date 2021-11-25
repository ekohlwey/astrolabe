package com.github.ekohlwey.astrolabe.devices

import com.github.ekohlwey.astrolabe.HostMessage
import com.github.ekohlwey.astrolabe.messages.DeviceMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface Device : AutoCloseable {
    val messages: Flow<DeviceMessage>

    suspend fun writeHostMessages(hostMessages: Flow<HostMessage>)
}