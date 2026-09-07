package com.sigynvs.phonejanitor.di

import android.content.Context
import com.sigynvs.phonejanitor.BuildConfig
import com.sigynvs.phonejanitor.cache.CacheInspector
import com.sigynvs.phonejanitor.email.EmailCredentialStore
import com.sigynvs.phonejanitor.email.GmailBulkMover
import com.sigynvs.phonejanitor.email.GmailImapClient
import com.sigynvs.phonejanitor.quarantine.QuarantineDatabase
import com.sigynvs.phonejanitor.quarantine.QuarantineStore
import com.sigynvs.phonejanitor.scan.DuplicateScanner
import com.sigynvs.phonejanitor.scan.FileScanner
import com.sigynvs.phonejanitor.scan.ScanSession
import com.sigynvs.phonejanitor.settings.SettingsRepository
import com.sigynvs.phonejanitor.update.UpdateChecker
import com.sigynvs.phonejanitor.update.UpdateInstaller
import com.sigynvs.phonejanitor.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph. One instance, held by [com.sigynvs.phonejanitor.PhoneJanitorApp].
 * No DI framework — the app is small and the wiring is flat.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val fileScanner: FileScanner by lazy { FileScanner() }

    val duplicateScanner: DuplicateScanner by lazy { DuplicateScanner() }

    val scanSession: ScanSession = ScanSession()

    private val database: QuarantineDatabase by lazy { QuarantineDatabase.build(appContext) }

    val quarantineStore: QuarantineStore by lazy {
        QuarantineStore(appContext, database.quarantineDao())
    }

    val emailCredentialStore: EmailCredentialStore by lazy { EmailCredentialStore(appContext) }
    val gmailClient: GmailImapClient by lazy { GmailImapClient() }
    val gmailBulkMover: GmailBulkMover by lazy { GmailBulkMover(appContext) }
    val cacheInspector: CacheInspector by lazy { CacheInspector(appContext) }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(
            checker = UpdateChecker(BuildConfig.VERSION_CODE),
            installer = UpdateInstaller(appContext),
            settings = settings,
            scope = appScope,
        )
    }
}
