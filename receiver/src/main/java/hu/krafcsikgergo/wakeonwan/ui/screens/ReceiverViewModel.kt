package hu.krafcsikgergo.wakeonwan.ui.screens

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.common.model.PairingPayload
import hu.krafcsikgergo.wakeonwan.common.model.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.receiver.PairingTokenManager
import hu.krafcsikgergo.wakeonwan.services.receiver.ServerData
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManager
import hu.krafcsikgergo.wakeonwan.services.receiver.SshKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * ViewModel for the Receiver screen that manages local server configuration.
 * Handles server settings, SSH credentials, and Ktor server service management.
 */
class ReceiverViewModel(
    private val dataStoreManager: DataStoreManager,
    private val wakeOnLanService: WakeOnLanService,
    private val sshManager: SSHManager,
    private val sshKeyManager: SshKeyManager,
    private val pairingTokenManager: PairingTokenManager,
    private val logManager: LogManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(ReceiverUiState())
        private set

    init {
        loadConfiguration()
        checkServerStatus()
        loadSshKeyState()
        ensurePairingToken()
    }

    /**
     * Makes sure a pairing token exists from the very first app launch onward,
     * rather than only generating one the first time the QR code is shown.
     */
    private fun ensurePairingToken() {
        if (!pairingTokenManager.hasToken()) {
            pairingTokenManager.generateToken()
        }
    }

    // Server Configuration Methods
    /**
     * Helper method to save ServerData to DataStore.
     */
    private suspend fun saveServerData(serverData: ServerData) {
        try {
            dataStoreManager.saveServerData(serverData)
        } catch (e: Exception) {
            logManager.e("ReceiverViewModel", "Error saving server data", e)
            updateErrorState("Failed to save server configuration: ${e.message}")
        }
    }

    /**
     * Updates the server IP address and saves it to configuration.
     */
    fun updateServerIpAddress(ipAddress: String) {
        val updatedServerData = uiState.serverData.copy(ipAddress = ipAddress)
        uiState = uiState.copy(serverData = updatedServerData)
        viewModelScope.launch {
            saveServerData(updatedServerData)
        }
    }

    /**
     * Updates the MAC address and saves it to configuration.
     */
    fun updateMacAddress(macAddress: String) {
        val updatedServerData = uiState.serverData.copy(macAddress = macAddress)
        uiState = uiState.copy(serverData = updatedServerData)
        viewModelScope.launch {
            saveServerData(updatedServerData)
        }
    }

    /**
     * Updates the SSH port and saves it to configuration.
     */
    fun updateSshPort(port: Int) {
        val updatedServerData = uiState.serverData.copy(sshPort = port)
        uiState = uiState.copy(serverData = updatedServerData)
        viewModelScope.launch {
            saveServerData(updatedServerData)
        }
    }

    /**
     * Updates the SSH username and saves it to configuration.
     */
    fun updateUsername(username: String) {
        val updatedServerData = uiState.serverData.copy(username = username)
        uiState = uiState.copy(serverData = updatedServerData)
        viewModelScope.launch {
            saveServerData(updatedServerData)
        }
    }

    // Ktor Server Management
    /**
     * Starts the Ktor server service.
     */
    fun startKtorServer() {
        uiState = uiState.copy(isKtorServerOperationInProgress = true)
        viewModelScope.launch {
            try {
                val success = wakeOnLanService.startKtorServer()
                if (success) {
                    val serverStarted = wakeOnLanService.serverStarted
                    logManager.d(
                        "ReceiverViewModel",
                        "Server started successfully. Timestamp: $serverStarted"
                    )
                    uiState = uiState.copy(
                        isKtorServerRunning = true,
                        isKtorServerOperationInProgress = false,
                        lastOperationMessage = "Ktor server started successfully",
                        serverStarted = serverStarted,
                        errorMessage = null
                    )
                } else {
                    uiState = uiState.copy(
                        isKtorServerOperationInProgress = false,
                        errorMessage = "Failed to start Ktor server"
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isKtorServerOperationInProgress = false,
                    errorMessage = "Error starting Ktor server: ${e.message}"
                )
            }
        }
    }

    /**
     * Stops the Ktor server service.
     */
    fun stopKtorServer() {
        uiState = uiState.copy(isKtorServerOperationInProgress = true)
        viewModelScope.launch {
            try {
                val success = wakeOnLanService.stopKtorServer()
                if (success) {
                    uiState = uiState.copy(
                        isKtorServerRunning = false,
                        isKtorServerOperationInProgress = false,
                        lastOperationMessage = "Ktor server stopped successfully",
                        serverStarted = null,
                        errorMessage = null
                    )
                } else {
                    uiState = uiState.copy(
                        isKtorServerOperationInProgress = false,
                        errorMessage = "Failed to stop Ktor server"
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isKtorServerOperationInProgress = false,
                    errorMessage = "Error stopping Ktor server: ${e.message}"
                )
            }
        }
    }

    /**
     * Checks the current status of the Ktor server service.
     */
    fun checkServerStatus() {
        viewModelScope.launch {
            try {
                val isRunning = wakeOnLanService.isKtorServerRunning()
                val serverStarted = wakeOnLanService.serverStarted
                logManager.d(
                    "ReceiverViewModel",
                    "Server status check - Running: $isRunning, Started: $serverStarted"
                )
                uiState = uiState.copy(
                    isKtorServerRunning = isRunning,
                    serverStarted = serverStarted,
                    errorMessage = null
                )
            } catch (e: Exception) {
                updateErrorState("Failed to check server status: ${e.message}")
            }
        }
    }

    /**
     * Sends a test Wake-on-LAN packet.
     */
    fun sendTestWakeOnLanPacket() {
        if (!isWakeOnLanConfigValid()) return

        uiState = uiState.copy(isTestWakeOnLanInProgress = true)
        viewModelScope.launch {
            try {
                val mac = uiState.serverData.macAddress
                logManager.d("ReceiverViewModel", "Sending Wake-on-LAN packet to MAC: $mac")

                val result = wakeOnLanService.sendWakeOnLanPacket(uiState.serverData)
                result.fold(
                    onSuccess = { message ->
                        uiState = uiState.copy(
                            isTestWakeOnLanInProgress = false,
                            lastOperationMessage = message,
                            errorMessage = null
                        )
                    },
                    onFailure = { exception ->
                        uiState = uiState.copy(
                            isTestWakeOnLanInProgress = false,
                            errorMessage = "Wake-on-LAN test failed: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isTestWakeOnLanInProgress = false,
                    errorMessage = "Wake-on-LAN test error: ${e.message}"
                )
            }
        }
    }

    /**
     * Tests the SSH connection using the current server configuration.
     */
    fun testSshConnection() {
        if (!isSshConfigValid()) return

        uiState = uiState.copy(isTestSshInProgress = true)
        viewModelScope.launch {
            try {

                val success = sshManager.testConnection(uiState.serverData)
                uiState = if (success) {
                    uiState.copy(
                        isTestSshInProgress = false,
                        lastOperationMessage = "SSH connection test successful",
                        errorMessage = null
                    )
                } else {
                    uiState.copy(
                        isTestSshInProgress = false,
                        errorMessage = "SSH connection test failed"
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isTestSshInProgress = false,
                    errorMessage = "SSH connection test error: ${e.message}"
                )
            }
        }
    }

    /**
     * Checks connectivity to the server: ping first, then (only if that succeeds)
     * a plain SSH connect/disconnect.
     */
    fun checkConnection() {
        if (!isSshConfigValid()) return

        uiState = uiState.copy(isCheckingConnection = true)
        viewModelScope.launch {
            try {
                val result = wakeOnLanService.checkConnection(uiState.serverData)
                uiState = if (result.sshSuccess) {
                    uiState.copy(
                        isCheckingConnection = false,
                        lastOperationMessage = result.message,
                        errorMessage = null
                    )
                } else {
                    uiState.copy(
                        isCheckingConnection = false,
                        errorMessage = result.message
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isCheckingConnection = false,
                    errorMessage = "Connection check error: ${e.message}"
                )
            }
        }
    }

    /**
     * Loads the current SSH key state (whether a key pair exists, and its public key).
     */
    private fun loadSshKeyState() {
        uiState = uiState.copy(
            hasSshKey = sshKeyManager.hasKeyPair(),
            sshPublicKey = sshKeyManager.getPublicKey()
        )
    }

    /**
     * Generates a new SSH key pair for authenticating to the server, replacing any
     * previously generated one. The resulting public key still needs to be added to
     * the server's authorized_keys.
     */
    fun generateSshKey() {
        uiState = uiState.copy(isGeneratingSshKey = true)
        viewModelScope.launch {
            try {
                val publicKey = withContext(Dispatchers.IO) { sshKeyManager.generateKeyPair() }
                uiState = uiState.copy(
                    isGeneratingSshKey = false,
                    hasSshKey = true,
                    sshPublicKey = publicKey,
                    lastOperationMessage = "SSH key generated. Add the public key to the server's authorized_keys.",
                    errorMessage = null
                )
                logManager.i("ReceiverViewModel", "Generated public key on device: $publicKey")
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isGeneratingSshKey = false,
                    errorMessage = "Failed to generate SSH key: ${e.message}"
                )
            }
        }
    }

    /**
     * Shows or hides the "Add Sender Device" pairing QR code. Generates a token
     * on first use if none exists yet.
     */
    fun toggleAddSenderDevice() {
        if (uiState.isShowingPairingQr) {
            uiState = uiState.copy(isShowingPairingQr = false)
            return
        }

        val payload = buildPairingQrPayload(forceNewToken = false)
        if (payload == null) {
            updateErrorState("Could not determine this device's local IP address")
            return
        }

        uiState = uiState.copy(isShowingPairingQr = true, pairingQrPayload = payload)
    }

    /**
     * Generates a brand new pairing token, invalidating any sender that was
     * paired with the previous one, and refreshes the displayed QR code.
     */
    fun regeneratePairingToken() {
        val payload = buildPairingQrPayload(forceNewToken = true)
        if (payload == null) {
            updateErrorState("Could not determine this device's local IP address")
            return
        }

        uiState = uiState.copy(
            pairingQrPayload = payload,
            lastOperationMessage = "New pairing code generated. Previously paired senders will need to re-scan.",
            errorMessage = null
        )
    }

    private fun buildPairingQrPayload(forceNewToken: Boolean): String? {
        val ipAddress = pairingTokenManager.getLocalIpAddress() ?: return null
        val token = if (forceNewToken || !pairingTokenManager.hasToken()) {
            pairingTokenManager.generateToken()
        } else {
            pairingTokenManager.getToken()!!
        }

        val payload = PairingPayload(
            name = Build.MODEL,
            ipAddress = ipAddress,
            port = defaultKtorPort,
            token = token
        )
        return Json.encodeToString(PairingPayload.serializer(), payload)
    }

    /**
     * Clears any error messages.
     */
    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun clearLastOperationMessage() {
        uiState = uiState.copy(lastOperationMessage = null)
    }

    /**
     * Loads the initial configuration from repository.
     */
    private fun loadConfiguration() {
        viewModelScope.launch {
            try {
                val serverData = dataStoreManager.getServerData() ?: return@launch
                uiState = uiState.copy(serverData = serverData)
            } catch (e: Exception) {
                logManager.e("ReceiverViewModel", "Error loading configuration", e)
                updateErrorState("Failed to load configuration: ${e.message}")
            }
        }
    }

    /**
     * Validates if the Wake-on-LAN configuration is valid.
     */
    private fun isWakeOnLanConfigValid(): Boolean {
        val isValid = uiState.serverData.ipAddress.isNotBlank() &&
                uiState.serverData.macAddress.isNotBlank() &&
                uiState.serverData.macAddress != "00:00:00:00:00:00"
        if (!isValid) {
            updateErrorState("Invalid Wake-on-LAN configuration: Please check IP and MAC address")
        }
        return isValid
    }

    /**
     * Validates if the SSH configuration is valid.
     */
    private fun isSshConfigValid(): Boolean {
        val isValid = uiState.serverData.ipAddress.isNotBlank() &&
                uiState.serverData.username.isNotBlank() &&
                sshKeyManager.hasKeyPair()
        if (!isValid) {
            updateErrorState("Invalid SSH configuration: Please check IP address, username, and generate an SSH key")
        }
        return isValid
    }

    /**
     * Updates the UI state with an error message.
     */
    private fun updateErrorState(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }
}

/**
 * UI state for the Receiver screen.
 */
data class ReceiverUiState(
    val serverData: ServerData = ServerData(
        ipAddress = "192.168.0.1",
        macAddress = "00:00:00:00:00:00",
        sshPort = 22,
        username = ""
    ),
    val isKtorServerRunning: Boolean = false,
    val isKtorServerOperationInProgress: Boolean = false,
    val isTestWakeOnLanInProgress: Boolean = false,
    val isTestSshInProgress: Boolean = false,
    val isCheckingConnection: Boolean = false,
    val hasSshKey: Boolean = false,
    val sshPublicKey: String? = null,
    val isGeneratingSshKey: Boolean = false,
    val isShowingPairingQr: Boolean = false,
    val pairingQrPayload: String? = null,
    val lastOperationMessage: String? = null,
    val errorMessage: String? = null,
    val serverStarted: Long? = null
)
