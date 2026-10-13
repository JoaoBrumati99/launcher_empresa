package com.amalfis.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager

class KioskManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_kiosk_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_PINNED_HOME_PACKAGES = "pinned_home_packages"
        private const val KEY_ADMIN_PIN = "admin_pin"
        private const val KEY_CORPORATE_EMAIL = "corporate_email"
        private const val KEY_WALLPAPER_PATH = "wallpaper_path"
        private const val DEFAULT_PIN = "1234"

        // Pacotes comuns liberados por padrão caso o TI não tenha configurado ainda
        val DEFAULT_WHITELIST = setOf(
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.waze",
            "com.google.android.apps.photos",
            "com.android.camera",
            "com.sec.android.app.camera",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.whatsapp",
            "com.google.android.gm",
            "com.sec.android.app.myfiles",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.apps.nbu.files"
        )
    }

    fun isAdminActive(): Boolean {
        return dpm.isAdminActive(adminComponent)
    }

    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Trava o cadastro e alteração de contas (Google, E-mail pessoal, etc) no Android.
     * Requer perfil de Device Owner ou Admin ativo com suporte.
     */
    fun setAccountModificationDisabled(disable: Boolean): Boolean {
        return try {
            if (disable) {
                if (isAdminActive()) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                }
            } else {
                if (isAdminActive()) {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isAccountModificationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            userManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)
        } else {
            false
        }
    }

    /**
     * Ativa ou desativa o Modo Kiosk (Lock Task Mode)
     */
    fun startKioskMode(activity: Activity, allowedPackages: Set<String>) {
        try {
            if (isDeviceOwner()) {
                val packagesToAllow = mutableListOf(context.packageName)
                packagesToAllow.addAll(allowedPackages)
                dpm.setLockTaskPackages(adminComponent, packagesToAllow.toTypedArray())
            }
            activity.startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopKioskMode(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAllowedPackages(): Set<String> {
        val saved = prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)
        if (saved == null) {
            saveAllowedPackages(DEFAULT_WHITELIST)
            return DEFAULT_WHITELIST
        }
        return saved
    }

    fun saveAllowedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, packages).apply()
    }

    fun getPinnedHomePackages(): Set<String> {
        val saved = prefs.getStringSet(KEY_PINNED_HOME_PACKAGES, null)
        if (saved == null) {
            val initial = emptySet<String>()
            savePinnedHomePackages(initial)
            return initial
        }
        return saved
    }

    fun savePinnedHomePackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PINNED_HOME_PACKAGES, packages).apply()
    }

    fun addPinnedHomePackage(pkg: String) {
        val current = getPinnedHomePackages().toMutableSet()
        current.add(pkg)
        savePinnedHomePackages(current)
    }

    fun removePinnedHomePackage(pkg: String) {
        val current = getPinnedHomePackages().toMutableSet()
        current.remove(pkg)
        savePinnedHomePackages(current)
    }

    fun checkAdminPin(inputPin: String): Boolean {
        return getAdminPin() == inputPin
    }

    fun getAdminPin(): String {
        return prefs.getString(KEY_ADMIN_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun saveAdminPin(newPin: String) {
        prefs.edit().putString(KEY_ADMIN_PIN, newPin).apply()
    }

    fun getCorporateEmail(): String {
        return prefs.getString(KEY_CORPORATE_EMAIL, "") ?: ""
    }

    fun saveCorporateEmail(email: String) {
        prefs.edit().putString(KEY_CORPORATE_EMAIL, email).apply()
    }

    fun getWallpaperPath(): String? {
        return prefs.getString(KEY_WALLPAPER_PATH, null)
    }

    fun saveWallpaperPath(path: String?) {
        prefs.edit().putString(KEY_WALLPAPER_PATH, path).apply()
    }

    fun clearWallpaperPath() {
        prefs.edit().remove(KEY_WALLPAPER_PATH).apply()
    }
}
