package com.webunime.mobile

import android.app.Application
import com.webunime.mobile.data.CatalogApi
import com.webunime.mobile.data.ads.RewardedAdManager
import com.webunime.mobile.data.auth.AuthRepository
import com.webunime.mobile.data.auth.WebunimeAuth
import com.webunime.mobile.data.billing.BillingRepository
import com.webunime.mobile.data.library.EpisodeAlerts
import com.webunime.mobile.data.library.FavoritesRepository
import com.webunime.mobile.data.party.WatchPartyApi
import com.webunime.mobile.data.push.NewContentPush
import com.webunime.mobile.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WebunimeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var catalogApi: CatalogApi
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var authRepository: AuthRepository
        private set
    /** Akun WEBUNIME (sama dengan web). */
    lateinit var webAuth: WebunimeAuth
        private set
    lateinit var watchParty: WatchPartyApi
        private set
    lateinit var favorites: FavoritesRepository
        private set
    lateinit var episodeAlerts: EpisodeAlerts
        private set
    lateinit var newContentPush: NewContentPush
        private set
    lateinit var rewardedAds: RewardedAdManager
        private set
    lateinit var billingRepository: BillingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        catalogApi = CatalogApi(BuildConfig.CATALOG_API_BASE)
        userRepository = UserRepository(this)
        authRepository = AuthRepository()
        webAuth = WebunimeAuth(this, BuildConfig.CATALOG_API_BASE)
        watchParty = WatchPartyApi(webAuth)
        favorites = FavoritesRepository(webAuth)
        episodeAlerts = EpisodeAlerts(this, webAuth, favorites, catalogApi)
        newContentPush = NewContentPush(this)
        rewardedAds = RewardedAdManager(this)
        billingRepository = BillingRepository(this)

        rewardedAds.init()
        billingRepository.start()

        appScope.launch { webAuth.restore() }
        EpisodeAlerts.ensureChannel(this)
        EpisodeAlerts.schedule(this)
        NewContentPush.ensureChannel(this)
        newContentPush.sync()

        // Ganti akun: muat favorit & kotak notifikasi akun itu, lalu cek episode baru.
        appScope.launch {
            webAuth.user.map { it?.id }.distinctUntilChanged().collect { uid ->
                episodeAlerts.loadInbox()
                if (uid == null) {
                    favorites.clear()
                } else {
                    runCatching { episodeAlerts.check() }
                }
            }
        }

        appScope.launch {
            userRepository.ensureBootstrapped()
            runCatching { userRepository.pullCloudIfSignedIn() }
        }

        appScope.launch {
            billingRepository.purchases.collect { purchase ->
                if (purchase.purchaseState != com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) {
                    return@collect
                }
                val productId = purchase.products.firstOrNull() ?: return@collect
                val plan = billingRepository.planForProduct(productId) ?: return@collect
                runCatching {
                    billingRepository.acknowledge(purchase)
                    userRepository.applyPremiumDays(plan.days, plan.bonusGems)
                }
            }
        }
    }
}
