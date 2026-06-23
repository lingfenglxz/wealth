package com.demo.wealth.data

import org.json.JSONObject

internal fun JSONObject.optFiniteDouble(name: String, fallback: Double = 0.0): Double =
    optDouble(name, fallback).takeIf { it.isFinite() } ?: fallback
