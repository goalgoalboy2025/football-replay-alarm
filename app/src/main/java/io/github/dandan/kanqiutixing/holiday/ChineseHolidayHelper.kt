package io.github.dandan.kanqiutixing.holiday

import android.util.Log
import io.github.dandan.kanqiutixing.data.local.HolidayDao
import io.github.dandan.kanqiutixing.data.local.HolidayEntity
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChineseHolidayHelper @Inject constructor(
    private val holidayDao: HolidayDao,
    private val timorApi: TimorApi
) {
    /**
     * 本次进程内已经尝试过加载的年份（无论成功失败）。
     * 关键作用：timor.tech 不可用时，避免对同一年反复发起会卡 ~20s 的网络请求——
     * 否则一次同步要给二三十场比赛各卡一次，累计就是十几分钟。
     *
     * 用线程安全的 set：本 helper 是 @Singleton，可能被 Worker、receiver 触发的
     * 多个协程并发访问。
     */
    private val attemptedYears: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    suspend fun isHoliday(date: LocalDate): Boolean {
        val cached = holidayDao.getByDate(date.toString())
        if (cached != null) return cached.isHoliday

        val ok = ensureYearLoaded(date.year)
        if (ok) {
            val again = holidayDao.getByDate(date.toString())
            if (again != null) return again.isHoliday
        }
        // 拿不到节假日数据 → 用"周末即假日"兜底，不阻塞
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    private suspend fun ensureYearLoaded(year: Int): Boolean {
        if (holidayDao.hasYear(year)) return true
        // 本次进程已经试过这一年但没成功（成功的话上面 hasYear 就 true 了）
        // → 直接放弃，不再卡网络。add 返回 false 表示之前已加入，原子操作避免并发重复请求
        if (!attemptedYears.add(year)) return false
        return try {
            val resp = timorApi.getYear(year)
            if (resp.code != 0) {
                Log.w(TAG, "timor.tech 返回 code=${resp.code}，$year 年节假日数据加载失败，改用周末兜底")
                return false
            }
            val items = resp.holiday.values.map {
                HolidayEntity(
                    date = it.date,
                    year = year,
                    isHoliday = it.holiday,
                    name = it.name
                )
            }
            holidayDao.insertAll(items)
            Log.i(TAG, "$year 年节假日数据加载成功，共 ${items.size} 条")
            true
        } catch (e: Exception) {
            Log.w(TAG, "timor.tech 请求失败（$year 年）：${e.message}，本次会话改用周末兜底")
            false
        }
    }

    companion object {
        private const val TAG = "KanqiuHoliday"
    }
}

interface TimorApi {
    @GET("api/holiday/year/{year}")
    suspend fun getYear(@Path("year") year: Int): TimorYearResponse
}

@JsonClass(generateAdapter = true)
data class TimorYearResponse(
    val code: Int,
    val holiday: Map<String, TimorDay>
)

@JsonClass(generateAdapter = true)
data class TimorDay(
    val holiday: Boolean,
    val name: String,
    val date: String
)
