package com.mandro.mark7.core.locale

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 앱 전용 언어(폰 언어와 별개) 저장·적용.
 *
 * DataStore 는 비동기라 [Context.attachBaseContext] 시점에 못 읽으므로 동기 접근이 되는
 * SharedPreferences 를 쓴다. 태그: `""`(시스템 따름) / `"ko"` / `"en"`.
 *
 * 주의: 화면 문자열이 아직 대부분 코드에 하드코딩(한국어)이라 실제 번역은 제한적이다.
 * 완전 번역은 문자열을 `res/values-en/strings.xml` 등으로 옮긴 뒤 자동 적용된다.
 */
object AppLocale {

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "lang_tag"

    val supportedTags = listOf("ko", "en")

    private val _currentTag = kotlinx.coroutines.flow.MutableStateFlow("ko")
    val currentTag = _currentTag.asStateFlow()

    fun tag(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
        val t = if (saved in supportedTags) saved!! else "ko"
        if (_currentTag.value != t) {
            _currentTag.value = t
        }
        return t
    }

    fun setTag(context: Context, tag: String) {
        val safeTag = if (tag in supportedTags) tag else "ko"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, safeTag)
            .apply()
        val locale = Locale.forLanguageTag(safeTag)
        Locale.setDefault(locale)

        // In-place resource configuration update (removes need for activity recreate)
        try {
            val res = context.resources
            val config = Configuration(res.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)

            val appRes = context.applicationContext.resources
            val appConfig = Configuration(appRes.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            appRes.updateConfiguration(appConfig, appRes.displayMetrics)
        } catch (_: Exception) {}

        _currentTag.value = safeTag
    }

    /** [base] 에 저장된 언어를 적용한 컨텍스트를 돌려준다. */
    fun wrap(base: Context): Context {
        val tag = tag(base)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** 태그를 기반으로 새 Configuration과 Context 생성 (리컴포지션 전용) */
    fun createLocalizedContext(base: Context, tag: String): Pair<Configuration, Context> {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return config to base.createConfigurationContext(config)
    }
}
