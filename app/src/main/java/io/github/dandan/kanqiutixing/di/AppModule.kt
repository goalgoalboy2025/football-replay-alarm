package io.github.dandan.kanqiutixing.di

import android.content.Context
import androidx.room.Room
import io.github.dandan.kanqiutixing.data.local.AppDatabase
import io.github.dandan.kanqiutixing.data.local.HolidayDao
import io.github.dandan.kanqiutixing.data.local.MIGRATION_1_2
import io.github.dandan.kanqiutixing.data.local.MatchDao
import io.github.dandan.kanqiutixing.data.local.NotificationDao
import io.github.dandan.kanqiutixing.data.local.TeamDao
import io.github.dandan.kanqiutixing.BuildConfig
import io.github.dandan.kanqiutixing.data.remote.FootballDataOrgApi
import io.github.dandan.kanqiutixing.holiday.TimorApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "kanqiu.db")
            .addMigrations(MIGRATION_1_2)
            // 有正式迁移时走迁移（保留数据）；遇到没写迁移的版本跳变才销毁重建
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTeamDao(db: AppDatabase): TeamDao = db.teamDao()
    @Provides fun provideMatchDao(db: AppDatabase): MatchDao = db.matchDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideHolidayDao(db: AppDatabase): HolidayDao = db.holidayDao()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // release 包关闭 HTTP 日志，避免 URL/参数泄漏到 logcat
        val level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        val log = HttpLoggingInterceptor().apply { this.level = level }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(log)
            .build()
    }

    @Provides
    @Singleton
    fun provideFootballDataOrg(client: OkHttpClient, moshi: Moshi): FootballDataOrgApi =
        Retrofit.Builder()
            .baseUrl("https://api.football-data.org/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FootballDataOrgApi::class.java)

    @Provides
    @Singleton
    fun provideTimorApi(client: OkHttpClient, moshi: Moshi): TimorApi =
        Retrofit.Builder()
            .baseUrl("https://timor.tech/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TimorApi::class.java)
}
