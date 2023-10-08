package com.comiccoder.weatherapp.models

import java.io.Serializable

data class Sys(
    val type: Int,
    val id: Long,
    val country: String,
    val sunrise: Long,
    val sunset: Long
): Serializable
