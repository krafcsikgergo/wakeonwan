package hu.krafcsikgergo.wakeonwan.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.services.sender.NetworkRepository
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.receiver.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.services.sender.KtorServerData
import kotlinx.coroutines.launch

/**
 * ViewModel for the Sender screen that manages remote server operations.
 * Handles network requests for wake-up, shutdown, and server status checks.
 */
class SenderViewModel(
    private val networkRepository: NetworkRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(SenderUiState())
        private set

    init {
        loadConfiguration()
    }

    /**
     * Updates the server IP address and saves it to configuration.
     */
    fun updateServerIpAddress(ipAddress: String) {
        val newSelectedServer = uiState.selectedKtorServer.copy(ipAddress = ipAddress)
        uiState = uiState.copy(selectedKtorServer = newSelectedServer)
    }

    /**
     * Updates the communication port and saves it to configuration.
     */
    fun updateCommunicationPort(port: Int) {
        val newSelectedServer = uiState.selectedKtorServer.copy(port = port)
        uiState = uiState.copy(selectedKtorServer = newSelectedServer)
    }

    /**
     * Tests the Ktor server health status.
     */
    fun testKtorServerStatus() {
        if (!isConfigurationValid()) return

        uiState = uiState.copy(ktorServerStatus = ServerStatus.LOADING)
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val isHealthy = networkRepository.checkServerHealth(baseUrl)
                uiState = uiState.copy(
                    ktorServerStatus = if (isHealthy) ServerStatus.LIVE else ServerStatus.DEAD,
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    ktorServerStatus = ServerStatus.DEAD,
                    errorMessage = "Ktor server test failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Tests the server status endpoint.
     */
    fun testServerStatus() {
        if (!isConfigurationValid()) return

        uiState = uiState.copy(serverStatus = ServerStatus.LOADING)
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val isLive = networkRepository.getServerStatus(baseUrl)
                uiState = uiState.copy(
                    serverStatus = if (isLive) ServerStatus.LIVE else ServerStatus.DEAD,
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    serverStatus = ServerStatus.DEAD,
                    errorMessage = "Server test failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a wake-up request to the remote server.
     */
    fun wakeUpServer() {
        if (!isConfigurationValid()) return

        uiState = uiState.copy(isWakeUpInProgress = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val result = networkRepository.wakeUpRemoteServer(baseUrl)
                result.fold(
                    onSuccess = { message ->
                        uiState = uiState.copy(
                            isWakeUpInProgress = false,
                            lastOperationMessage = message,
                            errorMessage = null
                        )
                    },
                    onFailure = { exception ->
                        uiState = uiState.copy(
                            isWakeUpInProgress = false,
                            errorMessage = "Wake-up failed: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isWakeUpInProgress = false,
                    errorMessage = "Wake-up error: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a shutdown request to the remote server.
     */
    fun shutdownServer() {
        if (!isConfigurationValid()) return

        uiState = uiState.copy(isShutdownInProgress = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val result = networkRepository.shutDownRemoteServer(baseUrl)
                result.fold(
                    onSuccess = { message ->
                        uiState = uiState.copy(
                            isShutdownInProgress = false,
                            lastOperationMessage = message,
                            errorMessage = null
                        )
                    },
                    onFailure = { exception ->
                        uiState = uiState.copy(
                            isShutdownInProgress = false,
                            errorMessage = "Shutdown failed: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isShutdownInProgress = false,
                    errorMessage = "Shutdown error: ${e.message}"
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

    fun saveNewKtorServer(newServer: KtorServerData) {
        viewModelScope.launch {
            try {
                dataStoreManager.addKtorServer(newServer)
                val updatedServers = dataStoreManager.getKtorServers()
                uiState = uiState.copy(
                    ktorServers = updatedServers,
                    selectedKtorServer = newServer
                )
            } catch (e: Exception) {
                updateErrorState("Failed to save server: ${e.message}")
            }
        }
    }

    fun deleteKtorServer(serverId: String) {
        viewModelScope.launch {
            try {
                dataStoreManager.removeKtorServer(serverId)
                val updatedServers = dataStoreManager.getKtorServers()
                val newSelectedServer = if (updatedServers.isNotEmpty()) updatedServers[0] else SenderUiState().selectedKtorServer
                uiState = uiState.copy(
                    ktorServers = updatedServers,
                    selectedKtorServer = newSelectedServer
                )
            } catch (e: Exception) {
                updateErrorState("Failed to delete server: ${e.message}")
            }
        }
    }

    fun selectKtorServer(serverId: String) {
        val selectedServer = uiState.ktorServers.find { it.id == serverId } ?: return
        uiState = uiState.copy(selectedKtorServer = selectedServer)
    }

    /**
     * Loads the initial configuration from DataStore.
     */
    private fun loadConfiguration() {
        viewModelScope.launch {
            try {
                val ktorServers = dataStoreManager.getKtorServers()
                uiState = uiState.copy(
                    ktorServers = ktorServers,
                    selectedKtorServer = if (ktorServers.isNotEmpty()) ktorServers[0] else SenderUiState().selectedKtorServer,
                )
            } catch (e: Exception) {
                updateErrorState("Failed to load configuration: ${e.message}")
            }
        }
    }

    /**
     * Constructs the base URL from the selected server configuration.
     * @return The base URL string (e.g., "http://192.168.1.100:8080")
     */
    private fun getBaseUrl(): String {
        val selectedServer = uiState.selectedKtorServer
        return "http://${selectedServer.ipAddress}:${selectedServer.port}"
    }

    /**
     * Validates if the current configuration is valid for network operations.
     */
    private fun isConfigurationValid(): Boolean {
        val selectedServer = uiState.selectedKtorServer
        val isValid = selectedServer?.ipAddress?.isNotBlank() == true && selectedServer.port > 0
        if (!isValid) {
            updateErrorState("Invalid configuration: Please check IP address and port")
        }
        return isValid
    }

    /**
     * Updates the UI state with an error message.
     */
    private fun updateErrorState(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up network resources
        networkRepository.close()
    }
}

/**
 * UI state for the Sender screen.
 */
data class SenderUiState(
    val ktorServers: List<KtorServerData> = emptyList(),
    val selectedKtorServer: KtorServerData = KtorServerData(
        id = "0",
        name = "Default",
        ipAddress = "",
        port = defaultKtorPort
    ),

    val serverStatus: ServerStatus = ServerStatus.UNKNOWN,
    val ktorServerStatus: ServerStatus = ServerStatus.UNKNOWN,
    val isWakeUpInProgress: Boolean = false,
    val isShutdownInProgress: Boolean = false,
    val lastOperationMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Represents the status of a server or service.
 */
enum class ServerStatus {
    LIVE, DEAD, UNKNOWN, LOADING
}