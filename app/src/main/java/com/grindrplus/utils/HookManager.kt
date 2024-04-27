package com.grindrplus.utils

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.hooks.AllowScreenshots
import com.grindrplus.hooks.ChatIndicators
import com.grindrplus.hooks.ChatTerminal
import com.grindrplus.hooks.DisableAnalytics
import com.grindrplus.hooks.DisableUpdates
import com.grindrplus.hooks.EnableUnlimited
import com.grindrplus.hooks.ExpiringPhotos
import com.grindrplus.hooks.Favorites
import com.grindrplus.hooks.FeatureGranting
import com.grindrplus.hooks.LocalSavedPhrases
import com.grindrplus.hooks.LocationSpoofer
import com.grindrplus.hooks.ModSettings
import com.grindrplus.hooks.OnlineIndicator
import com.grindrplus.hooks.ProfileDetails
import com.grindrplus.hooks.ProfileViews
import com.grindrplus.hooks.RemovableMessages
import com.grindrplus.hooks.UnlimitedAlbums
import com.grindrplus.hooks.UnlimitedProfiles
import com.grindrplus.hooks.UnlimitedTaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class HookManager {
    private var hooks = mutableMapOf<KClass<out Hook>, Hook>()

    private fun registerAndInitHooks() {
        runBlocking(Dispatchers.IO) {
            val hookList = listOf(
                EnableUnlimited(),
                AllowScreenshots(),
                ChatIndicators(),
                ChatTerminal(),
                DisableUpdates(),
                DisableAnalytics(),
                ExpiringPhotos(),
                Favorites(),
                FeatureGranting(),
                LocalSavedPhrases(),
                LocationSpoofer(),
                ModSettings(),
                OnlineIndicator(),
                UnlimitedProfiles(),
                ProfileDetails(),
                ProfileViews(),
                RemovableMessages(),
                UnlimitedTaps(),
                UnlimitedAlbums()
            )

            hookList.forEach { hook ->
                Config.initHookSettings(
                    hook.hookName, hook.hookDesc, true)
            }

            hooks = hookList.associateBy { it::class }.toMutableMap()

            hooks.values.forEach { hook ->
                if (Config.isHookEnabled(hook.hookName)) {
                    hook.init()
                    GrindrPlus.logger.log("Initialized hook: ${hook.hookName}")
                } else {
                    GrindrPlus.logger.log("Hook disabled: ${hook.hookName}")
                }
            }
        }
    }

    fun reloadHooks() {
        runBlocking(Dispatchers.IO) {
            hooks.values.forEach { hook -> hook.cleanup() }
            hooks.clear()
            registerAndInitHooks()
            GrindrPlus.logger.log("Hooks reloaded successfully.")
        }
    }

    fun init() {
        registerAndInitHooks()
    }
}