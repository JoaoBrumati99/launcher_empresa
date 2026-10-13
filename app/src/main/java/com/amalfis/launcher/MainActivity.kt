package com.amalfis.launcher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var kioskManager: KioskManager
    private lateinit var recyclerHomeScreenApps: RecyclerView
    private lateinit var txtNoPinnedApps: TextView
    private lateinit var imgCustomWallpaper: ImageView
    private lateinit var btnAdminSettings: ImageView

    // Weather Widget Views
    private lateinit var layoutWeatherWidget: View
    private lateinit var txtWeatherTemp: TextView
    private lateinit var txtWeatherCity: TextView
    private lateinit var txtWeatherRefreshTime: TextView

    private var appDrawerDialog: BottomSheetDialog? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateWeatherRefreshTime()
            clockHandler.postDelayed(this, 10000)
        }
    }

    private lateinit var gestureDetector: GestureDetector

    // Seletor de Foto de Fundo da Galeria
    private val pickWallpaperLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                saveAndApplyCustomWallpaper(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        makeSystemBarsTransparent()
        setContentView(R.layout.activity_main)

        kioskManager = KioskManager(this)

        // Initialize Views
        imgCustomWallpaper = findViewById(R.id.imgCustomWallpaper)
        btnAdminSettings = findViewById(R.id.btnAdminSettings)
        recyclerHomeScreenApps = findViewById(R.id.recyclerHomeScreenApps)
        txtNoPinnedApps = findViewById(R.id.txtNoPinnedApps)

        layoutWeatherWidget = findViewById(R.id.layoutWeatherWidget)
        txtWeatherTemp = layoutWeatherWidget.findViewById(R.id.txtWeatherTemp)
        txtWeatherCity = layoutWeatherWidget.findViewById(R.id.txtWeatherCity)
        txtWeatherRefreshTime = layoutWeatherWidget.findViewById(R.id.txtWeatherRefreshTime)

        recyclerHomeScreenApps.layoutManager = GridLayoutManager(this, 4)

        // Botão Visível de Configurações do TI
        btnAdminSettings.setOnClickListener {
            showAdminPinDialog()
        }

        // Acesso oculto alternativo segurando no widget do tempo
        layoutWeatherWidget.setOnLongClickListener {
            showAdminPinDialog()
            true
        }

        setupSwipeUpGesture()
        loadWallpaper()
        updateWeatherRefreshTime()
        loadHomeScreenApps()
        handleIncomingWallpaperIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingWallpaperIntent(intent)
    }

    private fun handleIncomingWallpaperIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        @Suppress("DEPRECATION")
        val dataUri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if ((action == Intent.ACTION_SET_WALLPAPER || action == Intent.ACTION_ATTACH_DATA) && dataUri != null) {
            saveAndApplyCustomWallpaper(dataUri)
        }
    }

    override fun onResume() {
        super.onResume()
        loadWallpaper()
        updateWeatherRefreshTime()
        loadHomeScreenApps()
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun makeSystemBarsTransparent() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupSwipeUpGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && (e1.y - e2.y > 50) && velocityY < -80) {
                    openAppDrawer()
                    return true
                }
                return false
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 != null && (e1.y - e2.y > 100)) {
                    openAppDrawer()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                showHomeScreenContextMenu()
            }
        })

        findViewById<View>(R.id.layoutBottomSwipeHint)?.setOnClickListener {
            openAppDrawer()
        }
    }

    private fun showHomeScreenContextMenu() {
        val options = arrayOf(
            getString(R.string.pick_wallpaper_button),
            getString(R.string.admin_panel_title)
        )

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickWallpaperLauncher.launch("image/*")
                    1 -> showAdminPinDialog()
                }
            }
            .show()
    }

    private fun updateWeatherRefreshTime() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        txtWeatherRefreshTime.text = String.format(Locale.getDefault(), "%s ↻", timeFormat.format(Date()))
    }

    private fun saveAndApplyCustomWallpaper(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "custom_wallpaper.jpg")
            val outputStream = FileOutputStream(file)

            if (inputStream != null) {
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()

                kioskManager.saveWallpaperPath(file.absolutePath)
                loadWallpaper()
                Toast.makeText(this, "Plano de fundo atualizado com sucesso!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao carregar a imagem: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadWallpaper() {
        val path = kioskManager.getWallpaperPath()
        if (path != null && File(path).exists()) {
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap != null) {
                imgCustomWallpaper.setImageBitmap(bitmap)
                imgCustomWallpaper.visibility = View.VISIBLE
                return
            }
        }
        imgCustomWallpaper.visibility = View.GONE
    }

    private fun removeCustomWallpaper() {
        val path = kioskManager.getWallpaperPath()
        if (path != null) {
            val file = File(path)
            if (file.exists()) file.delete()
        }
        kioskManager.clearWallpaperPath()
        imgCustomWallpaper.visibility = View.GONE
        Toast.makeText(this, "Foto de fundo removida.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Carrega e exibe os apps fixados na Página Inicial
     */
    private fun loadHomeScreenApps() {
        val allowedPackages = kioskManager.getAllowedPackages()
        val pinnedPackages = kioskManager.getPinnedHomePackages()
        val allApps = queryInstalledApps()

        val pinnedApps = allApps.filter { app ->
            allowedPackages.contains(app.packageName) &&
                    pinnedPackages.contains(app.packageName) &&
                    app.packageName != packageName
        }

        if (pinnedApps.isEmpty()) {
            txtNoPinnedApps.visibility = View.VISIBLE
            recyclerHomeScreenApps.visibility = View.GONE
        } else {
            txtNoPinnedApps.visibility = View.GONE
            recyclerHomeScreenApps.visibility = View.VISIBLE
            recyclerHomeScreenApps.adapter = AppAdapter(
                apps = pinnedApps,
                onAppClick = { selectedApp ->
                    launchApp(selectedApp.packageName)
                },
                onAppLongClick = { selectedApp ->
                    showHomeScreenItemMenu(selectedApp)
                }
            )
        }
    }

    /**
     * Abre a Gaveta de Aplicativos (App Drawer - Expandida até o topo e com fundo translúcido)
     */
    private fun openAppDrawer() {
        if (appDrawerDialog?.isShowing == true) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.layout_app_drawer, null, false)
        val recyclerDrawer = dialogView.findViewById<RecyclerView>(R.id.recyclerAppDrawer)
        val edtSearch = dialogView.findViewById<EditText>(R.id.edtSearchApps)
        val btnAdminDots = dialogView.findViewById<ImageView>(R.id.btnAdminDots)

        val allowedPackages = kioskManager.getAllowedPackages()
        val allApps = queryInstalledApps()

        val allowedApps = allApps.filter { app ->
            allowedPackages.contains(app.packageName) && app.packageName != packageName
        }

        val drawerAdapter = AppAdapter(
            apps = allowedApps,
            onAppClick = { app ->
                appDrawerDialog?.dismiss()
                launchApp(app.packageName)
            },
            onAppLongClick = { app ->
                showAppDrawerItemMenu(app)
            }
        )

        recyclerDrawer.layoutManager = GridLayoutManager(this, 4)
        recyclerDrawer.adapter = drawerAdapter

        // Busca em Tempo Real ("Pesquisar")
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase(Locale.getDefault())
                val filtered = if (query.isEmpty()) {
                    allowedApps
                } else {
                    allowedApps.filter { it.appName.lowercase(Locale.getDefault()).contains(query) }
                }
                drawerAdapter.updateData(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Acesso ao TI no ícone de 3 pontos na barra de busca
        btnAdminDots.setOnClickListener {
            appDrawerDialog?.dismiss()
            showAdminPinDialog()
        }

        appDrawerDialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog).apply {
            setContentView(dialogView)

            window?.setDimAmount(0.15f)

            val bottomSheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.isFitToContents = false
                behavior.expandedOffset = 0
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }

            show()
        }
    }

    private fun showHomeScreenItemMenu(app: AppInfo) {
        val options = arrayOf(
            getString(R.string.open_app),
            getString(R.string.remove_from_home)
        )

        AlertDialog.Builder(this)
            .setTitle(app.appName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchApp(app.packageName)
                    1 -> {
                        kioskManager.removePinnedHomePackage(app.packageName)
                        loadHomeScreenApps()
                        Toast.makeText(this, "${app.appName} removido da Página Inicial", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showAppDrawerItemMenu(app: AppInfo) {
        val options = arrayOf(
            getString(R.string.open_app),
            getString(R.string.add_to_home)
        )

        AlertDialog.Builder(this)
            .setTitle(app.appName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        appDrawerDialog?.dismiss()
                        launchApp(app.packageName)
                    }
                    1 -> {
                        kioskManager.addPinnedHomePackage(app.packageName)
                        loadHomeScreenApps()
                        Toast.makeText(this, "${app.appName} adicionado à Página Inicial", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun queryInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList = pm.queryIntentActivities(mainIntent, 0)
        val appList = mutableListOf<AppInfo>()
        val addedPackages = mutableSetOf<String>()
        val allowedPackages = kioskManager.getAllowedPackages()

        for (resolveInfo in resolveList) {
            val appName = resolveInfo.loadLabel(pm).toString()
            val pkgName = resolveInfo.activityInfo.packageName
            val icon = resolveInfo.loadIcon(pm)

            if (pkgName == packageName) continue

            val isAllowed = allowedPackages.contains(pkgName)
            appList.add(AppInfo(appName, pkgName, icon, isAllowed))
            addedPackages.add(pkgName)
        }

        // Garante a inclusão de Gerenciadores de Arquivos do sistema caso não estejam no CATEGORY_LAUNCHER
        val fileManagerPackages = listOf(
            "com.sec.android.app.myfiles",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.apps.nbu.files"
        )

        for (fmPkg in fileManagerPackages) {
            if (!addedPackages.contains(fmPkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(fmPkg, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isAllowed = allowedPackages.contains(fmPkg)
                    appList.add(AppInfo(appName, fmPkg, icon, isAllowed))
                    addedPackages.add(fmPkg)
                } catch (_: Exception) {}
            }
        }

        return appList.sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }

    private fun launchApp(packageName: String) {
        try {
            var launchIntent = packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent == null) {
                // Tratamento especial para gerenciadores de arquivos do sistema (ex: Samsung Meus Arquivos / Android Files)
                launchIntent = when (packageName) {
                    "com.sec.android.app.myfiles" -> {
                        Intent(Intent.ACTION_MAIN).apply {
                            setClassName("com.sec.android.app.myfiles", "com.sec.android.app.myfiles.external.ui.MainActivity")
                        }
                    }
                    "com.google.android.documentsui", "com.android.documentsui" -> {
                        Intent("android.provider.action.BROWSE").apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                        }
                    }
                    else -> {
                        Intent("android.intent.action.VIEW_DOWNLOADS")
                    }
                }
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent("android.intent.action.VIEW_DOWNLOADS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Erro ao abrir o app: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAdminPinDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_pin, null, false)
        val edtPin = dialogView.findViewById<EditText>(R.id.edtAdminPin)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val inputPin = edtPin.text.toString().trim()
                if (kioskManager.checkAdminPin(inputPin)) {
                    dialog.dismiss()
                    showAdminPanelDialog()
                } else {
                    Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAdminPanelDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_panel, null, false)

        val txtStatusOwner = dialogView.findViewById<TextView>(R.id.txtStatusDeviceOwner)
        val switchLockAccounts = dialogView.findViewById<SwitchMaterial>(R.id.switchLockAccounts)
        val edtEmail = dialogView.findViewById<EditText>(R.id.edtCorporateEmail)
        val btnSelectApps = dialogView.findViewById<Button>(R.id.btnSelectApps)
        val btnPickWallpaper = dialogView.findViewById<Button>(R.id.btnPickWallpaper)
        val btnClearWallpaper = dialogView.findViewById<Button>(R.id.btnClearWallpaper)
        val btnToggleKiosk = dialogView.findViewById<Button>(R.id.btnToggleKiosk)
        val btnWifi = dialogView.findViewById<Button>(R.id.btnWifiSettings)
        val btnSetDefault = dialogView.findViewById<Button>(R.id.btnSetDefaultLauncher)
        val btnChangePin = dialogView.findViewById<Button>(R.id.btnChangePin)
        val btnExitApp = dialogView.findViewById<Button>(R.id.btnExitApp)

        val isOwner = kioskManager.isDeviceOwner()
        val isAdmin = kioskManager.isAdminActive()
        txtStatusOwner.text = when {
            isOwner -> "Status: Device Owner / ODM Ativo ✓ (Controle Total)"
            isAdmin -> "Status: Administrador Ativo (Sem Device Owner)"
            else -> "Status: Modo Normal (Execute o comando dpm via ADB para Device Owner)"
        }

        switchLockAccounts.isChecked = kioskManager.isAccountModificationDisabled()
        switchLockAccounts.setOnCheckedChangeListener { _, isChecked ->
            val success = kioskManager.setAccountModificationDisabled(isChecked)
            if (success) {
                val statusMsg = if (isChecked) {
                    "Cadastro de contas de e-mail pessoais BLOQUEADO no dispositivo!"
                } else {
                    "Cadastro de contas liberado."
                }
                Toast.makeText(this, statusMsg, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Ação requer ativar o Administrador/Device Owner.", Toast.LENGTH_LONG).show()
                switchLockAccounts.isChecked = !isChecked
            }
        }

        edtEmail.setText(kioskManager.getCorporateEmail())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { d, _ ->
                val newEmail = edtEmail.text.toString().trim()
                kioskManager.saveCorporateEmail(newEmail)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        btnSelectApps.setOnClickListener {
            showSelectAppsDialog()
        }

        btnPickWallpaper.setOnClickListener {
            dialog.dismiss()
            pickWallpaperLauncher.launch("image/*")
        }

        btnClearWallpaper.setOnClickListener {
            removeCustomWallpaper()
        }

        btnToggleKiosk.setOnClickListener {
            val allowedPackages = kioskManager.getAllowedPackages()
            kioskManager.startKioskMode(this, allowedPackages)
            Toast.makeText(this, "Modo Kiosk Ativado!", Toast.LENGTH_SHORT).show()
        }

        btnWifi.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível abrir configurações de Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }

        btnSetDefault.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Abra as Configurações do Android > Apps Padrão > Launcher", Toast.LENGTH_LONG).show()
            }
        }

        btnChangePin.setOnClickListener {
            showChangePinDialog()
        }

        btnExitApp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.exit_app_button)
                .setMessage(R.string.exit_app_confirm)
                .setPositiveButton(R.string.confirm) { exitDialog, _ ->
                    exitDialog.dismiss()
                    dialog.dismiss()
                    kioskManager.stopKioskMode(this)
                    finishAndRemoveTask()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun showSelectAppsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_apps, null, false)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerAppsSelect)

        val installedApps = queryInstalledApps()
        val allowedSet = kioskManager.getAllowedPackages().toMutableSet()

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = AppSelectAdapter(installedApps) { app, isAllowed ->
            if (isAllowed) {
                allowedSet.add(app.packageName)
            } else {
                allowedSet.remove(app.packageName)
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                kioskManager.saveAllowedPackages(allowedSet)
                loadHomeScreenApps()
                Toast.makeText(this, "Lista de apps atualizada!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChangePinDialog() {
        val edtNewPin = EditText(this).apply {
            hint = "Novo PIN (ex: 4 dígitos)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Alterar PIN do TI")
            .setView(edtNewPin)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newPin = edtNewPin.text.toString().trim()
                if (newPin.length >= 4) {
                    kioskManager.saveAdminPin(newPin)
                    Toast.makeText(this, "Novo PIN salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "O PIN deve ter pelo menos 4 números.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
