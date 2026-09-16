package com.webunime.mobile

import android.app.Application
import com.webunime.mobile.data.CatalogApi
import com.webunime.mobile.data.ads.RewardedAdManager
import com.webunime.mobile.data.auth.AuthRepository
import com.webunime.mobile.data.billing.BillingRepository
import com.webunime.mobile.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WebunimeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var catalogApi: CatalogApi
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var rewardedAds: RewardedAdManager
        private set
    lateinit var billingRepository: BillingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        catalogApi = CatalogApi(this, BuildConfig.CATALOG_API_BASE)
        userRepository = UserRepository(this)
        authRepository = AuthRepository()
        rewardedAds = RewardedAdManager(this)
        billingRepository = BillingRepository(this)

        rewardedAds.init()
        billingRepository.start()

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
