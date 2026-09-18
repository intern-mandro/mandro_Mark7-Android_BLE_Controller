package com.mandro.mark7.di

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
     * BLE ↔ mock 을 실행 중에 바꾸는 단일 인스턴스. 앱은 항상 사용자 모드(실제 BLE)로 시작하고,
     * 개발자 모드는 [MockModeController.setMockMode] 로 그 실행(프로세스) 동안만 켤 수 있다 —
     * 앱을 재시작하거나 업데이트하면 다시 사용자 모드로 돌아온다.
     * 실제 구현은 [Provider] 라 처음 필요할 때에야 생성된다.
     */
    @Provides
    @Singleton
    fun provideSwitchableHandRepository(
        mock: FakeHandRepository,
        real: Provider<HandRepositoryImpl>,
    ): SwitchableHandRepository = SwitchableHandRepository(
        mock = mock,
        realFactory = real::get,
        initialMockMode = false,
    )

    @Provides
    fun provideHandRepository(repo: SwitchableHandRepository): HandRepository = repo

    @Provides
    fun provideMockModeController(repo: SwitchableHandRepository): MockModeController = repo
}
