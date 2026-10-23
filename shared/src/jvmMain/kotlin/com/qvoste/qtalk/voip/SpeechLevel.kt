package com.qvoste.qtalk.voip

// RTP silence/comfort noise can measure around -43 dBm0, not negative infinity.
internal fun isSpeechLevel(level: Float): Boolean = level.isFinite() && level > -35f
