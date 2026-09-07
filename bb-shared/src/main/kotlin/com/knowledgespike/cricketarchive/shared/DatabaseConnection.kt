package com.knowledgespike.cricketarchive.shared

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException

class DatabaseConnection(
    connectionString: String,
    userName: String?,
    password: String?
) {

//    val datasources = HashMap<String, DataSource>()

    private val log by LoggerDelegate()
    val connect: DbConnection
        get() = DbConnection(ds.connection, ds)

    val ds: DataSource

    init {

        var ds_local = DataSourceManager.datasources.get(connectionString)
        if (ds_local == null) {
            ds_local = DataSource()
            ds_local.configure(connectionString, userName ?: "", password ?: "")
            ds_local.debugLog("init")
            DataSourceManager.datasources.put(connectionString, ds_local)
        }
        ds = ds_local
    }

    fun debugLog(prefix: String) {
        ds.debugLog(prefix)
    }

    class DbConnection(val connection: Connection, val dataSource: DataSource) : Closeable {

        override fun close() {
            dataSource.debugLog("before close")
            connection.close()
            dataSource.debugLog("after close")
        }
    }
}

object DataSourceManager {
    val datasources = HashMap<String, DataSource>()

}

class DataSource {
    private val log by LoggerDelegate()
    private val config: HikariConfig = HikariConfig()
    private var ds: HikariDataSource? = null

    fun configure(connectionString: String, userName: String, password: String) {
        config.setJdbcUrl(connectionString)
        config.setUsername(userName)
        config.setPassword(password)
        config.isAutoCommit = false
        config.maximumPoolSize = 20

        config.idleTimeout = 1000 * 60 * 10;
        config.leakDetectionThreshold = 1000 * 60 * 10;
        config.validationTimeout = 1000 * 60 * 10;
        config.maxLifetime = 1000 * 60 * 10;
        config.connectionTimeout = 1000 * 60 * 10



        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("socketTimeout", "360000")

        // Equiv to cachePrepStmts=true and prepStmtCacheSize=250
        config.addDataSourceProperty("preparedStatementCacheQueries", "500")
        config.addDataSourceProperty("preparedStatementCacheSizeMiB", "10")
        // Optional: Switch to server-side preparation on the 1st execution
        config.addDataSourceProperty("prepareThreshold", "1")
        // Recommended: Optimize batch inserts
        config.addDataSourceProperty("reWriteBatchedInserts", "true")
        ds = HikariDataSource(config)
    }

    fun debugLog(prefix: String) {
        log.debug("$prefix - active connections: ${ds?.hikariPoolMXBean?.activeConnections}")
        log.debug("$prefix - idle connections: ${ds?.hikariPoolMXBean?.idleConnections}")
        log.debug("$prefix - total connections: ${ds?.hikariPoolMXBean?.totalConnections}")
    }

    @get:Throws(SQLException::class)
    val connection: Connection
        get() = ds!!.getConnection()
}
