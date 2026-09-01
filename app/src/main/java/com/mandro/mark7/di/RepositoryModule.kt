package com.mandro.mark7.di

import com.mandro.mark7.data.ble.HandRepositoryImpl
import com.mandro.mark7.domain.repository.HandRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHandRepository(impl: HandRepositoryImpl): HandRepository
}
