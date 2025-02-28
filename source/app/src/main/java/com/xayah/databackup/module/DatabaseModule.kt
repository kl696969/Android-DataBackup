package com.xayah.databackup.module

import android.content.Context
import androidx.room.Room
import com.xayah.databackup.data.AppDatabase
import com.xayah.databackup.data.CloudDao
import com.xayah.databackup.data.DirectoryDao
import com.xayah.databackup.data.LogDao
import com.xayah.databackup.data.MediaDao
import com.xayah.databackup.data.PackageBackupEntireDao
import com.xayah.databackup.data.PackageBackupOperationDao
import com.xayah.databackup.data.PackageRestoreEntireDao
import com.xayah.databackup.data.PackageRestoreOperationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "database-databackup").enableMultiInstanceInvalidation().build()

    @Provides
    @Singleton
    fun provideLogDao(database: AppDatabase): LogDao = database.logDao()

    @Provides
    @Singleton
    fun providePackageBackupEntireDao(database: AppDatabase): PackageBackupEntireDao = database.packageBackupEntireDao()

    @Provides
    @Singleton
    fun providePackageBackupOperationDao(database: AppDatabase): PackageBackupOperationDao = database.packageBackupOperationDao()

    @Provides
    @Singleton
    fun providePackageRestoreEntireDao(database: AppDatabase): PackageRestoreEntireDao = database.packageRestoreEntireDao()

    @Provides
    @Singleton
    fun providePackageRestoreOperationDao(database: AppDatabase): PackageRestoreOperationDao = database.packageRestoreOperationDao()

    @Provides
    @Singleton
    fun provideDirectoryDao(database: AppDatabase): DirectoryDao = database.directoryDao()

    @Provides
    @Singleton
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    @Singleton
    fun provideCloudDao(database: AppDatabase): CloudDao = database.cloudDao()
}
