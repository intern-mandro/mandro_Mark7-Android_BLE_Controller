package com.mandro.mark7.di

import com.mandro.mark7.BuildConfig
import com.mandro.mark7.data.ble.HandRepositoryImpl
import com.mandro.mark7.data.ble.HybridHandRepository
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
     * `USE_MOCK_BLE` (debug 빌드에서 true) 면 [HybridHandRepository] 를 바인딩한다 —
     * "Mark7 (mock)" 합성 기기는 그대로 두고, 그 아래에 실제 BLE 스캔 결과(`m…`/`mark…`)를
     * 함께 노출한다. mock 을 고르면 mock 경로, 실기기를 고르면 실제 BLE 경로로 라우팅한다.
     * 아니면(release) 실제 구현만 바인딩한다.
     * 쓰이지 않는 쪽은 [Provider] 라 생성되지 않는다.
     */
    @Provides
    @Singleton
    fun provideHandRepository(
        real: Provider<HandRepositoryImpl>,
        hybrid: Provider<HybridHandRepository>,
    ): HandRepository =
        if (BuildConfig.USE_MOCK_BLE) hybrid.get() else real.get()
}
