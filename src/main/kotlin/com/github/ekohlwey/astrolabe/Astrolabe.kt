package com.github.ekohlwey.astrolabe

import com.github.ekohlwey.astrolabe.ListPorts
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import kotlin.jvm.JvmStatic
import kotlin.system.exitProcess

@Command(name = "a7e", subcommands = [ListPorts::class], mixinStandardHelpOptions = true)
object Astrolabe {

    object AllowedLoggingValues : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return listOf("TRACE", "DEBUG",  "INFO", "WARN", "ERROR").iterator()
        }

    }

    @Option(
        description = ["Valid values: \${COMPLETION-CANDIDATES}"],
        completionCandidates = AllowedLoggingValues::class,
        defaultValue = "ERROR",
        names = ["-v", "--logLevel"]
    )
    var logLevel : String? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val logLevel = args.asIterable()
            .zipWithNext { arg, value -> if( arg in setOf("-v", "--logLevel")) value else null }
            .filterNotNull()
            .firstOrNull();
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel?:"ERROR")
        exitProcess(CommandLine(this).execute(*args))
    }
}