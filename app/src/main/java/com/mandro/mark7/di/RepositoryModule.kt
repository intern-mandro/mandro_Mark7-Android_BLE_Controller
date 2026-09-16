package com.mandro.mark7.di

import com.mandro.mark7.BuildConfig
import com.mandro.mark7.data.ble.FakeHandRepository
import com.mandro.mark7.data.ble.HandRepositoryImpl
import com.mandro.mark7.data.ble.SwitchableHandRepository
import com.mandro.mark7.domain.repository.HandRepository
import com.mandro.mark7.domain.repository.MockModeController
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
     * BLE ↔ mock 을 실행 중에 바꾸는 단일 인스턴스. 시작값은 `BuildConfig.USE_MOCK_BLE`
     * (build.gradle 빌드 타입별 true/false), 이후엔 [MockModeController.setMockMode] 로 바꾼다.
     * 실제 구현은 [Provider] 라 실제 모드가 처음 필요할 때에야 생성된다 → mock 동안 BLE 는 손대지 않는다.
     */
    @Provides
    @Singleton
    fun provideSwitchableHandRepository(
        mock: FakeHandRepository,
        real: Provider<HandRepositoryImpl>,
    ): SwitchableHandRepository = SwitchableHandRepository(
        mock = mock,
        realFactory = real::get,
        initialMockMode = BuildConfig.USE_MOCK_BLE,
    )

    @Provides
    fun provideHandRepository(repo: SwitchableHandRepository): HandRepository = repo

    @Provides
    fun provideMockModeController(repo: SwitchableHandRepository): MockModeController = repo
}
