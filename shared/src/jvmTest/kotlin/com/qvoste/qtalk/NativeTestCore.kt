package com.qvoste.qtalk

import org.linphone.core.*
import java.nio.file.Files

/** No native test may use the factory's default live call-log/key database. */
internal fun isolatedNativeCore(factory: Factory): Core {
    val dir = Files.createTempDirectory("qtalk-core-").toString().replace('\\', '/')
    val config = factory.createConfigFromString("""
        [storage]
        uri=$dir/main.sqlite3
        [lime]
        x3dh_db_path=$dir/x3dh.sqlite3
        [sip]
        call_logs_db=$dir/calls.sqlite3
        [zrtp]
        secrets_file=$dir/zrtp.sqlite3
    """.trimIndent())
    return factory.createCoreWithConfig(config, null).apply { isDatabaseEnabled = false }
}
