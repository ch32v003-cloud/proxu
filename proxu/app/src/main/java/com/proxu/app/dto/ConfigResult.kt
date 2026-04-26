package com.proxu.app.dto

data class ConfigResult(
    var status: Boolean,
    var guid: String? = null,
    var content: String = "",
)

