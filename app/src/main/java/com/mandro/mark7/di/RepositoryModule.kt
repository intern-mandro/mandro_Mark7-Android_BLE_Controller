package com.mandro.mark7.di

import com.mandro.mark7.BuildConfig
import com.mandro.mark7.data.ble.FakeHandRepository
import com.mandro.mark7.data.ble.HandRepositoryImpl
import com.mandro.mark7.domain.repository.HandRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    /**
     * `USE_MOCK_BLE` (debug 빌드에서 true) 면 실기기 없이 "연결된 상태"로 도는
     * [FakeHandRepository] 를, 아니면 실제 BLE 구현을 바인딩한다.
     * 쓰이지 않는 쪽은 [Provider] 라 생성되지 않는다.
     */
    @Provides
    @Singleton
    fun provideHandRepository(
        real: Provider<HandRepositoryImpl>,
        fake: Provider<FakeHandRepository>,
    ): HandRepository =
        if (BuildConfig.USE_MOCK_BLE) fake.get() else real.get()
}
