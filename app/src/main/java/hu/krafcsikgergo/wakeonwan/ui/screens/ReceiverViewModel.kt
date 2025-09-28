package hu.krafcsikgergo.wakeonwan.ui.screens

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.receiver.ServerData
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import kotlinx.coroutines.launch

/**
 * ViewModel for the Receiver screen that manages local server configuration.
 * Handles server settings, SSH credentials, and Ktor server service management.
 */
class ReceiverViewModel(
    private val dataStoreManager: DataStoreManager,
    private val wakeOnLanService: WakeOnLanService
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(ReceiverUiState())
        private set

    init {
        loadConfiguration()
        checkServerStatus()
    }

    // Server Configuration Methods
    /**
     * Helper method to save ServerData to DataStore.
     */
    private suspend fun saveServerData(serverData: ServerData) {
        try {
            dataStoreManager.saveServerData(serverData)
        } catch (e: Exception) {
            Log.e("ReceiverViewModel", "Error saving server data", e)
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

    /**
     * Updates the SSH password and saves it to configuration.
     */
    fun updatePassword(password: String) {
        val updatedServerData = uiState.serverData.copy(password = password)
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
                    Log.d("ReceiverViewModel", "Server started successfully. Timestamp: $serverStarted")
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
                Log.d("ReceiverViewModel", "Server status check - Running: $isRunning, Started: $serverStarted")
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
        username = "",
        password = ""
    ),
    val isKtorServerRunning: Boolean = false,
    val isKtorServerOperationInProgress: Boolean = false,
    val isTestWakeOnLanInProgress: Boolean = false,
    val lastOperationMessage: String? = null,
    val errorMessage: String? = null,
    val serverStarted: Long? = null
)