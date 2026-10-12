package com.lovelydays

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

fun Context.withRussianLocale(): Context {
    val locale = Locale.forLanguageTag("ru")
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
