package com.proxu.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator
import com.proxu.app.AppConfig
import com.proxu.app.R
import com.proxu.app.auth.ProxuApiModels
import com.proxu.app.auth.ProxuApiService
import com.proxu.app.auth.ProxuAuthManager
import com.proxu.app.auth.ProxuLoginActivity
import com.proxu.app.auth.ProxuProfileSync
import com.proxu.app.core.CoreServiceManager
import com.proxu.app.databinding.ActivityMainBinding
import com.proxu.app.enums.EConfigType
import com.proxu.app.enums.PermissionType
import com.proxu.app.extension.toast
import com.proxu.app.extension.toastError
import com.proxu.app.handler.AngConfigManager
import com.proxu.app.handler.MmkvManager
import com.proxu.app.handler.SettingsChangeManager
import com.proxu.app.handler.SettingsManager
import com.proxu.app.handler.SubscriptionUpdater
import com.proxu.app.util.LogUtil
import com.proxu.app.util.Utils
import com.proxu.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
    private var toolbarTitleView: android.widget.TextView? = null

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }
    private var loginGateVisible = false
    private var accountClickCount = 0
    private var hiddenMenuVisible = false
    private val authActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loginGateVisible = false
        updateAccountMenu()
        if (it.resultCode == RESULT_OK) {
            toast(R.string.auth_login_successful)
            // CRITICAL: Sync profiles from server after successful login
            // (onCreate won't run when returning via FLAG_ACTIVITY_CLEAR_TOP)
            lifecycleScope.launch {
                val token = ProxuAuthManager.getToken(this@MainActivity)
                if (!token.isNullOrBlank()) {
                    // Show progress dialog during sync so user can't click "Create Profile"
                    val progressDialog = android.app.ProgressDialog(this@MainActivity).apply {
                        setMessage(getString(R.string.auth_syncing_profiles))
                        setCancelable(false)
                        show()
                    }
                    try {
                        val result = ProxuProfileSync.syncProfilesAndSelectFirst(this@MainActivity, token)
                        LogUtil.i(AppConfig.TAG, "Post-login profile sync: ${result.message} (added=${result.added})")
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Post-login sync failed", e)
                    } finally {
                        progressDialog.dismiss()
                    }
                    // Refresh UI after sync completes (or fails)
                    setupGroupTab()
                    if (mainViewModel.subscriptionId != AppConfig.DEFAULT_SUBSCRIPTION_ID) {
                        mainViewModel.subscriptionIdChanged(AppConfig.DEFAULT_SUBSCRIPTION_ID)
                    } else {
                        mainViewModel.reloadServerList()
                    }
                    updateToolbarTitle()
                }
            }
        } else {
            // Refresh group tabs and server list after login dismissed
            if (SettingsChangeManager.consumeSetupGroupTab()) {
                setupGroupTab()
            }
            // CRITICAL: Set subscriptionId to default where proxu profiles are saved
            if (mainViewModel.subscriptionId != AppConfig.DEFAULT_SUBSCRIPTION_ID) {
                mainViewModel.subscriptionIdChanged(AppConfig.DEFAULT_SUBSCRIPTION_ID)
            } else {
                mainViewModel.reloadServerList()
            }
            updateToolbarTitle()
        }
        showLoginGateIfRequired()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.title_server))

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        // setup navigation drawer
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        updateAccountMenu()
        
        // Hide advanced menu items by default (easter egg: 10 clicks on Account to show)
        setHiddenMenuVisible(false)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        binding.fab.setOnClickListener { handleFabAction() }
        binding.layoutTest.setOnClickListener { handleLayoutTestClick() }

        setupGroupTab()
        setupViewModel()
        SubscriptionUpdater.sync()
        // reloadServerList is called in onResume() to ensure sync has completed
        updateToolbarTitle()

        // Auto-sync proxu profiles on startup if logged in (only on first creation, not rotation)
        if (savedInstanceState == null && ProxuAuthManager.isLoggedIn(this)) {
            lifecycleScope.launch {
                val token = ProxuAuthManager.getToken(this@MainActivity)
                if (!token.isNullOrBlank()) {
                    // Show progress dialog during startup sync to prevent "Create Profile" button flash
                    val progressDialog = android.app.ProgressDialog(this@MainActivity).apply {
                        setMessage(getString(R.string.auth_syncing_profiles))
                        setCancelable(false)
                        show()
                    }
                    try {
                        val result = ProxuProfileSync.syncProfilesAndSelectFirst(this@MainActivity, token)
                        LogUtil.i(AppConfig.TAG, "Startup profile sync: ${result.message} (added=${result.added})")
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Startup sync failed", e)
                    } finally {
                        progressDialog.dismiss()
                    }
                    // Refresh UI after sync completes
                    setupGroupTab()
                    if (mainViewModel.subscriptionId != AppConfig.DEFAULT_SUBSCRIPTION_ID) {
                        mainViewModel.subscriptionIdChanged(AppConfig.DEFAULT_SUBSCRIPTION_ID)
                    } else {
                        mainViewModel.reloadServerList()
                    }
                    updateToolbarTitle()
                }
            }
        }

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    private fun showLoginGateIfRequired() {
        if (loginGateVisible || ProxuAuthManager.isLoggedIn(this)) {
            return
        }
        loginGateVisible = true
        authActivityLauncher.launch(ProxuLoginActivity.createIntent(this, required = true))
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.updateListAction.observe(this) {
            val hasServers = mainViewModel.serversCache.isNotEmpty()
            binding.fab.isVisible = hasServers
            binding.layoutTest.isVisible = hasServers
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
        
        // Observe group tab changes (triggered by profile sync)
        SettingsChangeManager.setupGroupTabSignal.observe(this) {
            setupGroupTab()
        }
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        // CRITICAL: Ensure at least one group exists so ViewPager creates a fragment.
        // When all subscriptions are cleared (e.g., after logout/login), getSubscriptions()
        // may return an empty list if PREF_GROUP_ALL_DISPLAY is false.
        val effectiveGroups = if (groups.isEmpty()) {
            listOf(com.proxu.app.dto.GroupMapItem(id = "", remarks = getString(R.string.filter_config_all)))
        } else {
            groups
        }
        groupPagerAdapter.update(effectiveGroups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        updateToolbarTitle()

        val targetIndex = effectiveGroups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 } ?: (effectiveGroups.size - 1)
        binding.viewPager.setCurrentItem(targetIndex, false)

        binding.tabGroup.isVisible = effectiveGroups.size > 1
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // service not running: keep existing no-op (could show a message if desired)
        }
    }

    private fun updateToolbarTitle() {
        val balance = ProxuAuthManager.getBalance(this)
        val titleText = if (!balance.isNullOrBlank()) {
            "Баланс: $balance р."
        } else {
            getString(R.string.title_server)
        }
        supportActionBar?.setTitle(titleText)
        // Use single cached title view to avoid recreation on every balance update
        supportActionBar?.let { actionBar ->
            val tv = toolbarTitleView ?: android.widget.TextView(this).apply {
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.proxu_text_primary))
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    refreshBalanceManual()
                }
                actionBar.setDisplayShowTitleEnabled(false)
                actionBar.setDisplayShowCustomEnabled(true)
                actionBar.customView = this
                toolbarTitleView = this
            }
            tv.text = titleText
        }
    }

    private fun refreshBalanceManual() {
        lifecycleScope.launch {
            val token = ProxuAuthManager.getToken(this@MainActivity)
            if (!token.isNullOrBlank()) {
                try {
                    toast("Обновление баланса...")
                    val profile = withContext(Dispatchers.IO) {
                        ProxuApiService.getProfile(token)
                    }
                    
                    // Check if account is blocked
                    if (profile == null) {
                        val rawBody = withContext(Dispatchers.IO) {
                            ProxuApiService.getProfileRawString(token)
                        }
                        if (ProxuApiService.isBlockedResponse(rawBody)) {
                            toast(R.string.auth_account_blocked)
                            performLogout()
                            return@launch
                        }
                    }
                    
                    profile?.balance?.let { balance ->
                        val oldBalance = ProxuAuthManager.getBalance(this@MainActivity)
                        ProxuAuthManager.updateBalance(this@MainActivity, balance)
                        updateToolbarTitle()
                        updateAccountMenu()
                        val diff = (balance.toIntOrNull() ?: 0) - (oldBalance?.toIntOrNull() ?: 0)
                        if (diff != 0) {
                            toast("Баланс обновлён: $balance р. (изменение: ${if (diff > 0) "+$diff" else "$diff"})")
                        } else {
                            toast("Баланс: $balance р.")
                        }
                    } ?: toast("Не удалось получить баланс")
                } catch (e: Exception) {
                    LogUtil.e("MainActivity", "Manual balance refresh error: ${e.message}")
                    toast("Ошибка обновления баланса")
                }
            } else {
                toast("Войдите в аккаунт для просмотра баланса")
            }
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.fab.setIconResource(R.drawable.ic_fab_check)
            return
        }

        if (isRunning) {
            binding.fab.setIconResource(R.drawable.ic_stop_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.proxu_surface_glass))
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            setTestState(getString(R.string.connection_connected))
            binding.layoutTest.isFocusable = true
        } else {
            binding.fab.setIconResource(R.drawable.ic_play_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.connection_not_connected))
            binding.layoutTest.isFocusable = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccountMenu()
        // CRITICAL: Set subscriptionId to default where proxu profiles are saved
        // This ensures VPN profiles from proxu.pro are visible after login
        if (mainViewModel.subscriptionId != AppConfig.DEFAULT_SUBSCRIPTION_ID) {
            mainViewModel.subscriptionIdChanged(AppConfig.DEFAULT_SUBSCRIPTION_ID)
        } else {
            mainViewModel.reloadServerList()
        }
        updateToolbarTitle()
        binding.root.post { showLoginGateIfRequired() }
        
        // Check if returning from payment
        checkPendingPayment()
        
        // Start periodic balance sync (every 5 minutes)
        startBalanceSync()
    }
    
    private var balanceSyncJob: kotlinx.coroutines.Job? = null

    private fun startBalanceSync() {
        balanceSyncJob?.cancel()
        balanceSyncJob = lifecycleScope.launch {
            var tick = 0
            while (isActive) {
                // Check every 30 seconds if there is a pending payment, otherwise every 5 minutes
                val prefs = getSharedPreferences("proxu_auth", Context.MODE_PRIVATE)
                val hasPendingPayment = prefs.getString("pending_payment_id", null) != null
                val delayMs = if (hasPendingPayment) 30_000L else 300_000L // 30 sec or 5 min

                if (tick > 0 || hasPendingPayment) { // First immediate tick only if pending
                    delay(delayMs)
                }
                tick++

                if (!ProxuAuthManager.isLoggedIn(this@MainActivity)) continue
                val token = ProxuAuthManager.getToken(this@MainActivity)
                if (token.isNullOrBlank()) continue

                try {
                    // If pending payment exists, try to resolve it in background
                    if (hasPendingPayment) {
                        pollPendingPaymentInBackground()
                    }

                    // Regular balance refresh
                    val profile = withContext(Dispatchers.IO) {
                        ProxuApiService.getProfile(token)
                    }
                    
                    // Check if account is blocked
                    if (profile == null) {
                        val rawBody = withContext(Dispatchers.IO) {
                            ProxuApiService.getProfileRawString(token)
                        }
                        if (ProxuApiService.isBlockedResponse(rawBody)) {
                            withContext(Dispatchers.Main) {
                                toast(R.string.auth_account_blocked)
                                performLogout()
                            }
                            return@launch
                        }
                    }
                    
                    profile?.balance?.let { balance ->
                        val oldBalance = ProxuAuthManager.getBalance(this@MainActivity)
                        ProxuAuthManager.updateBalance(this@MainActivity, balance)
                        updateToolbarTitle()
                        updateAccountMenu()
                        val oldValue = oldBalance?.toIntOrNull() ?: 0
                        val newValue = balance.toIntOrNull() ?: 0
                        if (newValue != oldValue) {
                            LogUtil.i("MainActivity", "Balance changed: $oldValue -> $newValue")
                            // Do not show "balance topped up" during ordinary login/startup sync.
                            // Toast only if we are resolving an actual payment created in this app session.
                            if (hasPendingPayment && oldBalance != null && newValue > oldValue) {
                                toast("Баланс пополнен! Текущий баланс: $balance р.")
                            }
                        } else {
                            LogUtil.d("MainActivity", "Balance synced: $balance")
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e("MainActivity", "Balance sync failed", e)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        balanceSyncJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        // Hide advanced menu items when hidden menu is not visible (easter egg)
        menu.findItem(R.id.search_view)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.del_all_config)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.del_duplicate_config)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.del_invalid_config)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.export_all)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.ping_all)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.real_ping_all)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.sort_by_test_results)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.locate_selected_config)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.sub_update)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.service_restart)?.isVisible = hiddenMenuVisible
        // Hide entire Add config submenu
        menu.findItem(R.id.action_add)?.isVisible = hiddenMenuVisible
        menu.findItem(R.id.action_recharge)?.isVisible = ProxuAuthManager.isLoggedIn(this)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
            })

            searchView.setOnCloseListener {
                mainViewModel.filterConfig("")
                false
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_proxy_chain -> {
            importManually(EConfigType.PROXYCHAIN.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }

        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.locate_selected_config -> {
            locateSelectedServer()
            true
        }

        R.id.action_recharge -> {
            showRechargeDialog()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }

                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }


    /**
     * import config from sub
     */
    fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    /**
     * Locates and scrolls to the currently selected server.
     * If the selected server is in a different group, automatically switches to that group first.
     */
    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
        if (targetGroupIndex < 0) {
            toast(R.string.toast_server_not_found_in_group)
            return
        }

        // Switch to target group if needed, then scroll to the server
        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    /**
     * Scrolls to the selected server in the specified fragment.
     * @param groupIndex The index of the group/fragment to scroll in
     */
    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            toast(R.string.toast_fragment_not_available)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    private fun updateAccountMenu() {
        val isLoggedIn = ProxuAuthManager.isLoggedIn(this)
        val email = ProxuAuthManager.getUserEmail(this)
        // Update navigation drawer items
        binding.navView.menu.findItem(R.id.account)?.title = email ?: getString(R.string.auth_account)
        binding.navView.menu.findItem(R.id.transaction_history)?.isVisible = isLoggedIn
        binding.navView.menu.findItem(R.id.recharge)?.isVisible = isLoggedIn
        binding.navView.menu.findItem(R.id.logout)?.isVisible = isLoggedIn
        // Update overflow menu items visibility
        invalidateOptionsMenu()
    }

    private fun handleAccountClick() {
        // Easter egg: 10 clicks on account toggles hidden menu items
        accountClickCount++
        LogUtil.d("MainActivity", "Account clicked: $accountClickCount/10")
        if (accountClickCount >= 10) {
            accountClickCount = 0
            LogUtil.d("MainActivity", "Toggling hidden menu!")
            toggleHiddenMenu()
            return
        }

        if (ProxuAuthManager.isLoggedIn(this)) {
            ProxuAuthManager.getUserEmail(this)?.let { toast(it) } ?: toast(R.string.auth_account)
        } else {
            authActivityLauncher.launch(ProxuLoginActivity.createIntent(this))
        }
    }

    private fun setHiddenMenuVisible(visible: Boolean) {
        hiddenMenuVisible = visible
        mainViewModel.hiddenMenuVisible = visible
        updateHiddenMenuVisibility()
    }

    private fun toggleHiddenMenu() {
        hiddenMenuVisible = !hiddenMenuVisible
        val message = if (hiddenMenuVisible) getString(R.string.extended_menu_enabled) else getString(R.string.extended_menu_hidden)
        toast(message)
        updateHiddenMenuVisibility()
    }

    private fun updateHiddenMenuVisibility() {
        // Toggle entire hidden group visibility (works reliably with NavigationView)
        binding.navView.menu.setGroupVisible(R.id.group_hidden, hiddenMenuVisible)
        // Update recharge visibility based on login state
        updateAccountMenu()

        // Update toolbar menu visibility
        invalidateOptionsMenu()

        // Sync with ViewModel so adapters can hide/show action buttons
        mainViewModel.hiddenMenuVisible = hiddenMenuVisible
        // Refresh all visible server cards
        mainViewModel.reloadServerList()
    }

    private fun performLogout() {
        // Stop VPN service before clearing profiles (user no longer has valid access)
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
            toast(R.string.auth_vpn_stopped_on_logout)
        }
        // Clear pending payment marker from previous session to avoid false "balance topped up" toasts after next login
        getSharedPreferences("proxu_auth", Context.MODE_PRIVATE).edit().remove("pending_payment_id").apply()
        // Clear all proxu.pro VPN profiles on logout to ensure fresh sync on next login
        ProxuProfileSync.clearCloudProfiles()
        ProxuAuthManager.clearAuth(this)
        loginGateVisible = false
        // Clear WebView cookies/storage so next browser login starts fresh
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) { }
        updateAccountMenu()
        toast(R.string.auth_logout_successful)
        // CRITICAL: Rebuild UI tabs so old profiles disappear immediately
        setupGroupTab()
        mainViewModel.reloadServerList()
        binding.root.post { showLoginGateIfRequired() }
    }

    private fun showRechargeDialog() {
        if (!ProxuAuthManager.isLoggedIn(this)) {
            toast(R.string.auth_login_required)
            return
        }
        ProxuPaymentBottomSheetDialog(this) { amount, method ->
            createPayment(amount, method)
        }.show()
    }

    private fun createPayment(amount: Double, paymentMethod: String) {
        lifecycleScope.launch {
            val token = ProxuAuthManager.getToken(this@MainActivity)
            if (token.isNullOrBlank()) {
                toast(R.string.auth_login_required)
                return@launch
            }

            toast(R.string.proxu_recharge_creating)

            try {
                val response = withContext(Dispatchers.IO) {
                    ProxuApiService.createPayment(token, amount, paymentMethod)
                }

                if (response != null) {
                    LogUtil.d("MainActivity", "createPayment response: ${response.toString().take(500)}")
                    val paymentUrl = response.optString("payment_url", "")
                    // Try different possible keys for payment ID
                    val paymentId = response.optString("payment_id", "")
                        .ifBlank { response.optString("id", "") }
                        .ifBlank { response.optJSONObject("payment")?.optString("id", "") ?: "" }

                    LogUtil.d("MainActivity", "Payment URL: $paymentUrl, Payment ID: $paymentId")

                    if (paymentUrl.isNotEmpty() && paymentId.isNotEmpty()) {
                        // Save payment ID for confirmation
                        getSharedPreferences("proxu_auth", Context.MODE_PRIVATE)
                            .edit().putString("pending_payment_id", paymentId).apply()
                        LogUtil.d("MainActivity", "Saved pending_payment_id: $paymentId")
                        // Open payment URL
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                        startActivity(intent)
                        toast(R.string.proxu_recharge_success)
                    } else {
                        LogUtil.e("MainActivity", "Missing payment_url or payment_id in response")
                        toast(R.string.proxu_recharge_failed)
                    }
                } else {
                    toast(R.string.proxu_recharge_failed)
                }
            } catch (e: Exception) {
                LogUtil.e("MainActivity", "Payment creation error: ${e.message}")
                toast(R.string.proxu_recharge_failed)
            }
        }
    }

    private fun checkPendingPayment() {
        val prefs = getSharedPreferences("proxu_auth", Context.MODE_PRIVATE)
        val paymentId = prefs.getString("pending_payment_id", null)
        LogUtil.d("MainActivity", "checkPendingPayment: paymentId=$paymentId")

        if (paymentId != null) {
            // DO NOT clear pending_payment_id here - let refreshBalanceWithRetry clear it on success
            // Server may need time to process webhook from YooKassa
            refreshBalanceWithRetry(paymentId, maxAttempts = 10, delayMs = 5000L)
        }
    }

    /**
     * Aggressive balance refresh with retry for payment confirmation.
     * Polling every 5 seconds up to 10 attempts (50 seconds total).
     * Clears pending_payment_id only on confirmed balance change.
     */
    private fun refreshBalanceWithRetry(
        paymentId: String,
        attempt: Int = 1,
        maxAttempts: Int = 10,
        delayMs: Long = 5000L,
        silent: Boolean = false
    ) {
        lifecycleScope.launch {
            val token = ProxuAuthManager.getToken(this@MainActivity)
            if (token.isNullOrBlank()) {
                LogUtil.w("MainActivity", "Token is null, cannot refresh balance")
                return@launch
            }

            try {
                // Get current balance BEFORE server call
                val oldBalance = ProxuAuthManager.getBalance(this@MainActivity)
                val oldValue = oldBalance?.toIntOrNull() ?: 0

                // Always refresh balance from server (regardless of payment status)
                val rawProfile = withContext(Dispatchers.IO) {
                    ProxuApiService.getProfileRaw(token)
                }
                LogUtil.e("MainActivity", "RAW PROFILE attempt $attempt: ${rawProfile?.toString()?.take(500)}")

                val profile = rawProfile?.let { ProxuApiModels.parseUserProfile(it) }
                val newBalance = profile?.balance
                val newValue = newBalance?.toIntOrNull() ?: 0
                val diff = newValue - oldValue

                LogUtil.e("MainActivity", "Balance check attempt $attempt: old=$oldValue, new=$newValue, diff=$diff")

                if (newBalance != null) {
                    ProxuAuthManager.updateBalance(this@MainActivity, newBalance)
                    updateToolbarTitle()
                    updateAccountMenu()
                }

                // Success case: balance actually increased
                if (diff > 0) {
                    val msg = "Баланс пополнен на $diff руб.! Текущий баланс: $newBalance р."
                    if (!silent) toast(msg)
                    LogUtil.d("MainActivity", msg)
                    // Clear pending payment - confirmed!
                    clearPendingPayment(paymentId)
                    return@launch
                }

                // Also check payment status endpoint for additional confirmation
                val status = withContext(Dispatchers.IO) {
                    ProxuApiService.getPaymentStatus(token, paymentId)
                }
                LogUtil.d("MainActivity", "Payment status (attempt $attempt): ${status?.toString()?.take(500)}")

                val paymentStatus = status?.optString("status", "") ?: ""
                val isSucceeded = paymentStatus.equals("succeeded", true) ||
                        paymentStatus.equals("paid", true) ||
                        paymentStatus.equals("completed", true) ||
                        status?.optBoolean("success", false) == true

                if (isSucceeded) {
                    if (diff == 0 && newBalance != null) {
                        // Payment succeeded but balance same - server webhook may be delayed
                        if (attempt < maxAttempts) {
                            LogUtil.d("MainActivity", "Payment succeeded but balance not updated yet. Retrying in ${delayMs}ms...")
                            delay(delayMs)
                            refreshBalanceWithRetry(paymentId, attempt + 1, maxAttempts, delayMs, silent)
                        } else {
                            LogUtil.d("MainActivity", "Payment succeeded but balance not updated after max attempts. Will retry in background sync.")
                        }
                        return@launch
                    }
                } else if (paymentStatus.equals("pending", true) || paymentStatus.isBlank() || paymentStatus.equals("wait_for_capture", true)) {
                    // Payment still processing
                    if (attempt < maxAttempts) {
                        LogUtil.d("MainActivity", "Payment pending (status=$paymentStatus), retrying in ${delayMs}ms...")
                        delay(delayMs)
                        refreshBalanceWithRetry(paymentId, attempt + 1, maxAttempts, delayMs, silent)
                    } else {
                        LogUtil.d("MainActivity", "Payment still pending after max attempts. Will retry in background sync.")
                        // Keep pending_payment_id for background sync
                    }
                    return@launch
                } else if (paymentStatus.equals("canceled", true) || paymentStatus.equals("failed", true)) {
                    // Payment failed
                    LogUtil.d("MainActivity", "Payment canceled or failed: $paymentStatus")
                    clearPendingPayment(paymentId)
                    return@launch
                } else {
                    LogUtil.d("MainActivity", "Payment status: $paymentStatus")
                    clearPendingPayment(paymentId)
                }
            } catch (e: Exception) {
                LogUtil.e("MainActivity", "Payment check error (attempt $attempt): ${e.message}", e)
                if (attempt < maxAttempts) {
                    delay(delayMs)
                    refreshBalanceWithRetry(paymentId, attempt + 1, maxAttempts, delayMs, silent)
                } else {
                    LogUtil.d("MainActivity", "Payment check failed after max attempts. Will retry in background sync.")
                }
            }
        }
    }

    private fun clearPendingPayment(expectedId: String) {
        val prefs = getSharedPreferences("proxu_auth", Context.MODE_PRIVATE)
        val currentId = prefs.getString("pending_payment_id", null)
        if (currentId == expectedId) {
            prefs.edit().remove("pending_payment_id").apply()
            LogUtil.d("MainActivity", "Cleared pending_payment_id: $expectedId")
        }
    }

    private fun pollPendingPaymentInBackground() {
        val prefs = getSharedPreferences("proxu_auth", Context.MODE_PRIVATE)
        val paymentId = prefs.getString("pending_payment_id", null)
        if (paymentId != null) {
            LogUtil.d("MainActivity", "Background sync: found pending payment $paymentId, checking silently...")
            // Silent = true: do not show toasts during background checks
            refreshBalanceWithRetry(paymentId, maxAttempts = 3, delayMs = 10000L, silent = true)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.account -> {
                handleAccountClick()
                // Don't close drawer - allow multiple clicks for easter egg (10 clicks toggles hidden menu)
                return true
            }
            R.id.transaction_history -> startActivity(Intent(this, TransactionHistoryActivity::class.java))
            R.id.recharge -> showRechargeDialog()
            R.id.logout -> performLogout()
            R.id.sub_setting -> requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
            R.id.per_app_proxy_settings -> requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
            R.id.routing_setting -> requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.user_asset_setting -> requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
            R.id.settings -> requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
            R.id.promotion -> Utils.openUri(this, "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}")
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
            R.id.backup_restore -> requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }
}