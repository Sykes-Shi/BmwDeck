package io.github.shaonialife.cardeck.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.shaonialife.cardeck.iap2.Iap2SenderSession
import io.github.shaonialife.cardeck.transport.Iap2LinkChannel
import io.github.shaonialife.cardeck.transport.StreamDuplexStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * 手机侧（iPhone 角色）**主动连接车机**的无线 iAP2 RFCOMM 客户端 —— 真车阶段新增。
 *
 * 系统配对后，此客户端负责主动连接车机提供的无线 iAP2 服务。
 * [Iap2BluetoothServer] 同时保留监听，处理由车机发起的连接。
 *
 * 连上后复用与测试台完全相同的发送端 bring-up（[Iap2SenderSession]），
 * 成功时回调 [onEndpoint] 交出热点凭据与 AirPlay 端点。
 */
class Iap2BluetoothClient(
    private val context: Context,
    private val log: (String) -> Unit,
    private val onEndpoint: (Iap2SenderSession.CarPlayEndpoint) -> Unit = {},
    /** 0x5703 一到即触发（绑 WiFi + 广播 _carplay-ctrl），真车 0x4301 的前置条件。 */
    private val onWifiCredentials: (Iap2SenderSession.WifiConfiguration) -> Unit = {},
    /** Phase 1 之后车机补发 0x4301（CarPlay 窗口晚开）时交给主控热更新端点。 */
    private val onEndpointUpdate: (Iap2SenderSession.CarPlayEndpoint) -> Unit = {},
    /** 目标车机（动态读取：选择后无需重启链路，下一轮尝试即生效）。 */
    private val targetProvider: () -> TargetDevicePrefs.Target? = { null },
    /** Read once per connection start. */
    private val shouldDisconnectProfiles: () -> Boolean = { true },
) {
    companion object {
        /**
         * 配件端（车机）无线 iAP2 服务 UUID。
         * 手机端 UUID 是 …cafe（xcertplay IAP2_IPHONE_UUID），配件端是 …caff。
         */
        val ACCESSORY_IAP2_UUID: UUID = UUID.fromString("00000000-deca-fade-deca-deafdecacaff")

        private const val SESSION_TIMEOUT_MILLIS = 90_000L
        private const val RETRY_DELAY_MILLIS = 3_000L
        private const val FAILURE_LOG_THROTTLE_MILLIS = 15_000L

        /** 「蓝牙已连接→已断开」日志节流（车机可能反复自动重连，避免刷屏）。 */
        private const val PROFILE_LOG_THROTTLE_MILLIS = 15_000L

        /** SDP 服务探测等待窗（fetchUuidsWithSdp → ACTION_UUID 广播）。 */
        private const val SDP_PROBE_TIMEOUT_MILLIS = 2_500L
    }

    private var thread: Thread? = null
    private val currentSocket = AtomicReference<BluetoothSocket?>(null)
    private val socketLock = Any()
    private val profileSideEffectLock = Any()

    /**
     * 客户端线程世代号：start()/stop() 都会递增。卡在阻塞 connect() 里的线程被
     * stop() 解除阻塞后（关闭 connecting socket），会因世代不符直接退出——
     * 防止「stop 后线程残活、用户再次启动时新旧两代并发连车机」
     * （2026-10-11 实测：多个僵尸线程并发 RFCOMM 会让车机频繁拆链）。
     */
    private val generation =
        java.util.concurrent.atomic
            .AtomicLong(0)

    @Volatile
    private var activeChannel: Iap2LinkChannel? = null

    @Volatile
    private var running = false

    /** 目标设备不在已配对列表的提示节流（避免每 3 秒刷屏）。 */
    private var lastTargetMissingLogAt = 0L

    /** 「无 iAP2 服务→跳过」日志去重（每地址一次即可，重复轮询不刷屏）。 */
    private val skippedNoService = java.util.Collections.synchronizedSet(HashSet<String>())

    /** 「是否提供 iAP2 服务」结论缓存（地址→结论）：SDP 查询不便宜，勿每 3s 重查。 */
    private val iap2ServiceCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Check automatic candidates. Cache only observed service availability. */
    @SuppressLint("MissingPermission")
    private fun awaitIap2Service(device: BluetoothDevice): BluetoothConnectionDiagnostics.SdpOutcome {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        fun report(outcome: BluetoothConnectionDiagnostics.SdpOutcome): BluetoothConnectionDiagnostics.SdpOutcome {
            log("BT-C: SDP status=${outcome.name} elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt}")
            return outcome
        }
        val address = try { device.address } catch (_: SecurityException) {
            return report(BluetoothConnectionDiagnostics.SdpOutcome.PERMISSION_DENIED)
        }
        if (iap2ServiceCache[address] == true) {
            return report(BluetoothConnectionDiagnostics.SdpOutcome.PRESENT)
        }
        try {
            if (device.uuids?.any { it.uuid == ACCESSORY_IAP2_UUID } == true) {
                iap2ServiceCache[address] = true
                return report(BluetoothConnectionDiagnostics.SdpOutcome.PRESENT)
            }
        } catch (_: SecurityException) {
            return report(BluetoothConnectionDiagnostics.SdpOutcome.PERMISSION_DENIED)
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        val outcome = AtomicReference<BluetoothConnectionDiagnostics.SdpOutcome?>(null)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: android.content.Intent) {
                try {
                    @Suppress("DEPRECATION")
                    val hit = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (hit?.address != address) return
                    @Suppress("DEPRECATION")
                    val values = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                        ?.mapNotNull { (it as? android.os.ParcelUuid)?.uuid }
                    outcome.set(BluetoothConnectionDiagnostics.fromUuids(values, ACCESSORY_IAP2_UUID))
                } catch (_: SecurityException) {
                    outcome.set(BluetoothConnectionDiagnostics.SdpOutcome.PERMISSION_DENIED)
                } catch (_: RuntimeException) {
                    outcome.set(BluetoothConnectionDiagnostics.SdpOutcome.UNAVAILABLE)
                } finally {
                    if (outcome.get() != null) latch.countDown()
                }
            }
        }
        var registered = false
        try {
            ContextCompat.registerReceiver(context, receiver,
                android.content.IntentFilter(BluetoothDevice.ACTION_UUID), ContextCompat.RECEIVER_EXPORTED)
            registered = true
            if (!device.fetchUuidsWithSdp()) return report(BluetoothConnectionDiagnostics.SdpOutcome.UNAVAILABLE)
            if (!latch.await(SDP_PROBE_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return report(BluetoothConnectionDiagnostics.SdpOutcome.TIMEOUT)
            }
            val result = outcome.get() ?: BluetoothConnectionDiagnostics.SdpOutcome.UNAVAILABLE
            if (BluetoothConnectionDiagnostics.shouldCache(result)) iap2ServiceCache[address] = true
            return report(result)
        } catch (_: SecurityException) {
            return report(BluetoothConnectionDiagnostics.SdpOutcome.PERMISSION_DENIED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return BluetoothConnectionDiagnostics.SdpOutcome.CANCELLED
        } catch (_: RuntimeException) {
            return report(BluetoothConnectionDiagnostics.SdpOutcome.UNAVAILABLE)
        } finally {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** 「蓝牙已连接→已断开」日志节流时间戳。 */
    private var lastProfileLogAt = 0L

    val isRunning: Boolean get() = running

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) {
            log("BT-C: 缺少 BLUETOOTH_CONNECT 权限")
            return false
        }
        val adapter =
            try {
                context.getSystemService(BluetoothManager::class.java)?.adapter
            } catch (_: Exception) {
                null
            }
        if (adapter == null || !adapter.isEnabled) {
            log("BT-C: 蓝牙不可用或未开启")
            return false
        }
        // 每轮启动重置 SDP 结论与跳过日志（新配对/换固件后重新探测）
        iap2ServiceCache.clear()
        skippedNoService.clear()
        val disconnectProfiles = runCatching { shouldDisconnectProfiles() }.getOrDefault(true)
        running = true
        val myGeneration = generation.incrementAndGet()
        val target = targetProvider()
        log(
            "BT-C: 启动——将主动连入车机的无线 iAP2 服务（…caff）" +
                if (target != null) {
                    "（只连接已选目标）"
                } else {
                    "（未指定目标：依次尝试全部候选设备，可在「连接准备」中指定）"
                },
        )
        Thread({ connectLoop(myGeneration, disconnectProfiles) }, "iap2-bt-client").apply {
            isDaemon = true
            thread = this
            start()
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun connectLoop(myGeneration: Long, disconnectProfiles: Boolean) {
        val adapter =
            try {
                context.getSystemService(BluetoothManager::class.java)?.adapter
            } catch (_: Exception) {
                null
            } ?: return
        var lastFailureLogAt = 0L
        val snapshottedTargets = mutableSetOf<String>()
        val backoff = BluetoothConnectionDiagnostics.ProbeBackoff()
        while (running && generation.get() == myGeneration) {
            // 静默降噪（2026-10-10 用户实测后启用）——会话期间完全静默，不产生任何连接尝试/日志：
            //  - inboundActive：接收端正在连我们（两条 RFCOMM 会互相踩；Audi 实测双会话混乱）
            //  - sessionActive：CarPlay 会话已建立（已拿到端点）——与其它接收端的连接尝试全部暂停
            //    典型场景：连着 Redmi/DiPlay 时不再无脑探测车机 Audi（消除日志噪音与蓝牙churn）；
            //    会话结束（任一端 BT 会话终结 / 手动断开）时自动恢复。
            if (BtSessionGate.inboundActive || BtSessionGate.sessionActive) {
                sleepQuietly(2_000)
                continue
            }
            val candidates = candidateDevices(adapter)
            if (candidates.isEmpty()) {
                log("BT-C: 没有已配对的蓝牙设备——请先在系统设置里与车机完成蓝牙配对")
                sleepQuietly(RETRY_DELAY_MILLIS * 3)
                continue
            }
            var connected: BluetoothSocket? = null
            var sessionAllowed: (() -> Boolean)? = null
            for (device in candidates) {
                if (!running || generation.get() != myGeneration) return
                runCatching { adapter.cancelDiscovery() }
                val selectedTarget = targetProvider()
                val explicit = selectedTarget != null
                if (selectedTarget != null && TargetDevicePrefs.matches(selectedTarget, device.address) &&
                    snapshottedTargets.add(device.address)) {
                    logSelectedCachedServices(device)
                }
                // A selected target attempts RFCOMM without an additional SDP request.
                if (!explicit) {
                    if (!backoff.shouldProbe(device.address, android.os.SystemClock.elapsedRealtime())) continue
                    val outcome = awaitIap2Service(device)
                    if (!running || generation.get() != myGeneration || outcome == BluetoothConnectionDiagnostics.SdpOutcome.CANCELLED) return
                    backoff.note(device.address, android.os.SystemClock.elapsedRealtime(), outcome)
                    if (outcome != BluetoothConnectionDiagnostics.SdpOutcome.PRESENT) {
                        if (skippedNoService.add(device.address)) log("BT-C: 候选设备的 iAP2 服务未确认，跳过本次连接")
                        continue
                    }
                }
                if (!running || generation.get() != myGeneration) return
                if (BtSessionGate.inboundActive || BtSessionGate.sessionActive) continue
                fun targetStillAllowed(): Boolean {
                    val current = targetProvider()
                    return BluetoothConnectionDiagnostics.targetStillAllowed(
                        explicit, current != null, current != null && TargetDevicePrefs.matches(current, device.address),
                    )
                }
                if (!targetStillAllowed()) continue
                fun attemptStillValid(): Boolean = running && generation.get() == myGeneration &&
                    !Thread.currentThread().isInterrupted && targetStillAllowed() &&
                    !BtSessionGate.inboundActive && !BtSessionGate.sessionActive
                if (disconnectProfiles) {
                    val before = BtProfiles.disconnect(context, device, ::attemptStillValid) { action ->
                        synchronized(profileSideEffectLock) {
                            if (attemptStillValid()) { action(); true } else false
                        }
                    }
                    if (before.isNotEmpty()) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastProfileLogAt > PROFILE_LOG_THROTTLE_MILLIS) {
                            lastProfileLogAt = now
                            val result = BtProfiles.disconnectResult(context, device.address, before)
                            log("BT-C: 通话/媒体蓝牙断开操作：$result")
                        }
                    }
                }
                if (!running || generation.get() != myGeneration) return
                if (!targetStillAllowed() || BtSessionGate.inboundActive || BtSessionGate.sessionActive) continue
                val allowed = { attemptStillValid() }
                val socket = tryConnect(device, myGeneration, allowed)
                if (socket != null) {
                    log("BT-C: ✓ 无线 iAP2 服务的 RFCOMM 已建立")
                    connected = socket
                    sessionAllowed = allowed
                    break
                } else {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastFailureLogAt > FAILURE_LOG_THROTTLE_MILLIS) {
                        lastFailureLogAt = now
                        log("BT-C: RFCOMM 未建立，稍后重试")
                    }
                }
            }
            val sessionSocket = connected
            if (sessionSocket == null) {
                sleepQuietly(RETRY_DELAY_MILLIS)
                continue
            }
            val sessionCheck = sessionAllowed
            if (generation.get() != myGeneration || sessionCheck == null || !sessionCheck()) {
                // socket 已过期；当前代次可继续等待或连接新目标。
                currentSocket.compareAndSet(sessionSocket, null)
                runCatching { sessionSocket.close() }
                if (!BluetoothConnectionDiagnostics.retryAfterInvalidSocket(running, generation.get() == myGeneration)) return
                continue
            }
            runSession(sessionSocket, myGeneration, sessionCheck)
            sleepQuietly(RETRY_DELAY_MILLIS)
        }
        log("BT-C: 客户端已停止")
    }

    /** Only inspect the system cache once per selected device; this performs no SDP request. */
    @SuppressLint("MissingPermission")
    private fun logSelectedCachedServices(device: BluetoothDevice) {
        val state = try {
            BluetoothConnectionDiagnostics.cachedServices(device.uuids?.map { it.uuid }, ACCESSORY_IAP2_UUID)
        } catch (_: SecurityException) {
            "count=unknown iap2=unknown status=permission_denied"
        } catch (_: RuntimeException) {
            "count=unknown iap2=unknown status=unavailable"
        }
        log("BT-C: SDP cached $state（系统缓存可能不是最新）")
    }

    /**
     * 候选设备：优先 SDP 缓存里带配件端 UUID 的已配对设备；
     * 缓存缺失时退化为全部已配对设备（connect 时的 SDP 查询会自行过滤）。
     * 指定了目标车机时**只返回该设备**（用户配对了多台车机时避免误连）。
     */
    @SuppressLint("MissingPermission")
    private fun candidateDevices(adapter: android.bluetooth.BluetoothAdapter): List<BluetoothDevice> {
        val bonded =
            try {
                adapter.bondedDevices?.toList().orEmpty()
            } catch (_: SecurityException) {
                emptyList()
            }
        targetProvider()?.let { target ->
            // 目标策略：地址命中即唯一候选——不再按 SDP 缓存过滤（缓存可能缺失，
            // 而用户显式指定就该尝试）；目标不在已配对列表时给一次提示并空转等待。
            val hit = bonded.filter { TargetDevicePrefs.matches(target, it.address) }
            if (hit.isEmpty()) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastTargetMissingLogAt > FAILURE_LOG_THROTTLE_MILLIS) {
                    lastTargetMissingLogAt = now
                    log(
                        "BT-C: 已选目标不在已配对列表" +
                            "——请到系统蓝牙设置完成配对，或在「连接准备」改选目标",
                    )
                }
                return emptyList()
            }
            return hit
        }
        val withService =
            bonded.filter { device ->
                device.uuids?.any { it.uuid == ACCESSORY_IAP2_UUID } == true
            }
        return if (withService.isNotEmpty()) withService else bonded
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(device: BluetoothDevice, myGeneration: Long, allowed: () -> Boolean): BluetoothSocket? {
        fun failed(stage: String, startedAt: Long, error: Exception) {
            val code = BluetoothConnectionDiagnostics.socketErrorCode(error)
            log("BT-C: RFCOMM stage=$stage result=failed type=${error.javaClass.simpleName} " +
                "category=${BluetoothConnectionDiagnostics.errorCategory(error)} " +
                "socketCode=${code ?: "unavailable"} " +
                "socketCodeName=${BluetoothConnectionDiagnostics.socketErrorCodeLabel(code)} " +
                "elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt}")
        }
        val createAt = android.os.SystemClock.elapsedRealtime()
        val socket = try {
            device.createRfcommSocketToServiceRecord(ACCESSORY_IAP2_UUID)
        } catch (error: Exception) {
            failed("create", createAt, error)
            return null
        }
        log("BT-C: RFCOMM stage=create result=success elapsedMs=${android.os.SystemClock.elapsedRealtime() - createAt}")
        val registered = synchronized(profileSideEffectLock) {
            synchronized(socketLock) {
                if (!running || generation.get() != myGeneration || !allowed()) {
                    false
                } else {
                    currentSocket.set(socket)
                    true
                }
            }
        }
        if (!registered) {
            runCatching { socket.close() }
            return null
        }
        val connectAt = android.os.SystemClock.elapsedRealtime()
        return try {
            socket.connect()
            if (!running || generation.get() != myGeneration || !allowed()) {
                currentSocket.compareAndSet(socket, null)
                runCatching { socket.close() }
                return null
            }
            log("BT-C: RFCOMM stage=connect result=success elapsedMs=${android.os.SystemClock.elapsedRealtime() - connectAt}")
            socket
        } catch (error: Exception) {
            currentSocket.compareAndSet(socket, null)
            runCatching { socket.close() }
            if (running && generation.get() == myGeneration) failed("connect", connectAt, error)
            null
        }
    }

    private fun runSession(socket: BluetoothSocket, myGeneration: Long, allowed: () -> Boolean) {
        val stream = try {
            StreamDuplexStream(socket.inputStream, socket.outputStream) {
                runCatching { socket.close() }
            }
        } catch (_: IOException) {
            currentSocket.compareAndSet(socket, null)
            runCatching { socket.close() }
            return
        }
        val channel = Iap2LinkChannel(stream, name = "iap2-bt-car") { line -> log(line) }
        val accepted = synchronized(socketLock) {
            if (!running || generation.get() != myGeneration || !allowed()) {
                currentSocket.compareAndSet(socket, null)
                false
            } else {
                activeChannel = channel
                true
            }
        }
        if (!accepted) {
            channel.close()
            runCatching { socket.close() }
            return
        }
        try {
            channel.runSenderSession(context, SESSION_TIMEOUT_MILLIS, log, onEndpoint, onWifiCredentials, onEndpointUpdate)
        } catch (error: IOException) {
            log("BT-C: SESSION 失败 type=${error.javaClass.simpleName} category=${BluetoothConnectionDiagnostics.errorCategory(error)}")
        } finally {
            synchronized(socketLock) {
                if (BluetoothConnectionDiagnostics.ownsSession(activeChannel, channel)) activeChannel = null
                currentSocket.compareAndSet(socket, null)
            }
            runCatching { socket.close() }
            channel.close()
            log("BT-C: 本轮会话结束（客户端仍在运行，将自动重连）")
        }
    }

    /** 主动结束当前 BT 控制会话（不影响重连循环）。供 AirPlay 配对前复现控制通道退役状态。 */
    fun detachCurrentSession() {
        val (socket, channel) = synchronized(socketLock) {
            val old = currentSocket.getAndSet(null) to activeChannel
            activeChannel = null
            old
        }
        channel?.let {
            channel.close()
            log("BT-C: 已主动断开当前控制会话")
        }
        socket?.let { runCatching { it.close() } }
    }

    fun stop() {
        val (socket, channel) = synchronized(socketLock) {
            running = false
            generation.incrementAndGet()
            val old = currentSocket.getAndSet(null) to activeChannel
            activeChannel = null
            old
        }
        socket?.let { runCatching { it.close() } }
        channel?.let { runCatching { it.close() } }
        thread?.interrupt()
        thread = null
        log("BT-C: 已停止")
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
        }
    }
}

/**
 * 入站会话门闸：车机主动连入的 iAP2 会话活跃期间，出站客户端暂停连接尝试。
 * 目的：车机通常只有单条无线 iAP2 上下文，双向同时建链会让双方互相干扰。
 */
object BtSessionGate {
    /** 入站（车机主动连入）的 iAP2 会话活跃：出站暂停，避免两条 RFCOMM 互相踩。 */
    @Volatile
    var inboundActive: Boolean = false

    /**
     * CarPlay 会话已建立（任一路径成功拿到端点后置位，由 SessionController 维护）：
     * 暂停一切出站连接尝试（静默降噪）——典型表现为连着 DiPlay 时不再探测 Audi。
     * 会话结束/手动断开时由 SessionController 清除。
     */
    @Volatile
    var sessionActive: Boolean = false
}
