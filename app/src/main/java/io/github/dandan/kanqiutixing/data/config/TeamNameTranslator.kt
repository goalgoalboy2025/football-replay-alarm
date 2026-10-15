package io.github.dandan.kanqiutixing.data.config

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 球队英文名 → 中文名翻译。
 *
 * 数据来自 assets/team_names_zh.json（key 为 football-data.org 返回的英文名）。
 * 找不到对应中文时，原样返回英文名——不会崩，只是这一支队暂时显示英文。
 * 用户看到未翻译的队，往 team_names_zh.json 里加一行即可。
 */
@Singleton
class TeamNameTranslator @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val adapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    private val map: Map<String, String> by lazy {
        try {
            val json = context.assets.open("team_names_zh.json")
                .bufferedReader().use { it.readText() }
            adapter.fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** 英文队名翻成中文；没有对应翻译就返回原英文名 */
    fun toZh(englishName: String?): String {
        if (englishName.isNullOrBlank()) return "待定"
        return map[englishName] ?: englishName
    }
}
