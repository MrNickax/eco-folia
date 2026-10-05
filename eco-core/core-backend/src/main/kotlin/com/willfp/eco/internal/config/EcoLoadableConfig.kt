package com.willfp.eco.internal.config

import com.willfp.eco.core.PluginLike
import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.interfaces.LoadableConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.util.logging.Level

open class EcoLoadableConfig(
    type: ConfigType,
    configName: String,
    private val plugin: PluginLike,
    private val subDirectoryPath: String,
    val source: Class<*>,
    private val requiresChangesToSave: Boolean
) : EcoConfig(type), LoadableConfig {
    private val configFile: File
    private val name: String = "$configName.${type.extension}"
    private var hasChanged = false
    private val header = mutableListOf<String>()
    private val saveLock = Any()

    fun reloadFromFile() {
        runCatching { init(configFile) }.onFailure { it.printStackTrace() }
    }

    final override fun createFile() {
        val outFile = File(this.plugin.dataFolder, resourcePath)
        val lastIndex = resourcePath.lastIndexOf('/')
        val outDir = File(this.plugin.dataFolder, resourcePath.substring(0, lastIndex.coerceAtLeast(0)))
        if (!outDir.exists()) {
            outDir.mkdirs()
        }
        if (!outFile.exists()) {
            source.getResourceAsStream(resourcePath)!!.use { inputStream ->
                FileOutputStream(outFile).use { out ->
                    inputStream.copyTo(out)
                }
            }
        }
    }

    override fun getResourcePath(): String {
        val resourcePath: String = if (subDirectoryPath.isEmpty()) {
            name
        } else {
            subDirectoryPath + name
        }
        return "/$resourcePath"
    }

    @Throws(IOException::class)
    override fun save() {
        if (requiresChangesToSave) {
            if (!hasChanged) { // In order to preserve comments
                return
            }
        }

        // Build the content before touching the file, then replace it atomically
        AtomicFileWriter.write(snapshot(), durable = false)
    }

    /**
     * Save like [save], and also sync the directory so the save survives a power loss.
     *
     * Blocks on two disk syncs, so only for callers already off the main and region threads.
     */
    @Throws(IOException::class)
    fun saveDurably() {
        if (requiresChangesToSave) {
            if (!hasChanged) { // In order to preserve comments
                return
            }
        }

        AtomicFileWriter.write(snapshot(), durable = true)
    }

    override fun saveAsync() {
        // Snapshot now, then replace the file durably on the background writer
        AtomicFileWriter.writeAsync(snapshot())
            .whenComplete { _, error ->
                if (error != null) {
                    plugin.logger.log(Level.SEVERE, "Could not save ${configFile.path}", error.cause ?: error)
                }
            }
    }

    /**
     * Build the content and take its place in the write order under one lock.
     */
    private fun snapshot(): AtomicFileWriter.Snapshot = synchronized(saveLock) {
        if (!configFile.exists()) {
            plugin.logger.warning("${configFile.path} was missing and is being recreated")
        }

        AtomicFileWriter.snapshot(configFile.toPath(), this.toPlaintext().toByteArray())
    }

    private fun makeHeader(contents: String) {
        header.clear()

        if (this.type == ConfigType.YAML) {
            for (line in contents.lines()) {
                if (!line.startsWith("#")) {
                    break
                }

                header.add(line)
            }
        }
    }

    protected fun init(reader: Reader) {
        val string = reader.readToString()
        makeHeader(string)
        super.init(type.toMap(string), emptyMap())
    }

    fun init(file: File) {
        init(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
    }

    override fun toPlaintext(): String {
        val contents = StringBuilder()

        if (this.type == ConfigType.YAML) {
            for (s in header) {
                contents.append(s + "\n")
            }

            if (header.isNotEmpty()) {
                contents.append("\n")
            }
        }

        for (line in super.toPlaintext().lines()) {
            if (line.startsWith("#")) {
                continue
            }

            contents.append(line + "\n")
        }

        return contents.toString()
    }

    override fun getName(): String {
        return name
    }

    override fun getConfigFile(): File {
        return configFile
    }

    override fun set(path: String, obj: Any?) {
        hasChanged = true
        super.set(path, obj)
    }

    init {
        val directory = File(this.plugin.dataFolder, subDirectoryPath)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        if (!File(directory, name).exists()) {
            createFile()
        }
        configFile = File(directory, name)
        init(configFile)
        addToHandler(plugin)
    }

    private fun addToHandler(plugin: PluginLike) {
        plugin.configHandler.addConfig(this)
    }
}
