package com.webunime.mobile.data.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.webunime.mobile.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RewardedAdManager(context: Context) {

    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private var initializing = false

    fun init() {
        if (initializing) return
        initializing = true
        MobileAds.initialize(appContext) {
            preload()
        }
    }

    fun preload() {
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            },
        )
    }

    suspend fun show(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        val ad = rewardedAd
        if (ad == null) {
            preload()
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                preload()
                if (cont.isActive) cont.resume(false)
            }
        }
        ad.show(activity) {
            if (cont.isActive) cont.resume(true)
        }
    }
}
