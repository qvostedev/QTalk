package com.qvoste.qtalk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform