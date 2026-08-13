package hu.krafcsikgergo.wakeonwan.sender.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.common.model.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.sender.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.sender.services.KtorServerData
import hu.krafcsikgergo.wakeonwan.sender.services.NetworkRepository
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
        viewModelScope.launch {
            try {
                val ktorServers = dataStoreManager.getKtorServers()
                uiState = uiState.copy(
                    ktorServers = ktorServers,
                )

                // set selected if ktorServers is not empty
                if (ktorServers.isNotEmpty()) {
                    selectKtorServer(ktorServers[0].id)
                }
            } catch (e: Exception) {
                updateErrorState("Failed to load configuration: ${e.message}")
            }
        }
    }

    /**
     * Tests the Ktor server health status.
     */
    fun testKtorServerStatus() {
        uiState = uiState.copy(ktorServerStatus = ServerStatus.LOADING)
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val isHealthy = networkRepository.checkKtorAppHealth(baseUrl)
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
     * Tests the target server's connectivity: ping first, then (only if that
     * succeeds) SSH.
     */
    fun testServerStatus() {
        uiState = uiState.copy(
            serverPingStatus = ServerStatus.LOADING,
            serverSshStatus = ServerStatus.LOADING
        )
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val result = networkRepository.getServerStatus(baseUrl, getSelectedServerToken())
                uiState = uiState.copy(
                    serverPingStatus = if (result.pingSuccess) ServerStatus.LIVE else ServerStatus.DEAD,
                    serverSshStatus = if (result.sshSuccess) ServerStatus.LIVE else ServerStatus.DEAD,
                    errorMessage = if (result.isUnauthorized) result.message else null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    serverPingStatus = ServerStatus.DEAD,
                    serverSshStatus = ServerStatus.DEAD,
                    errorMessage = "Server test failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Sends a wake-up request to the remote server.
     */
    fun wakeUpServer() {
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
                        // The shutdown response can't reliably confirm the server actually
                        // powered off (its own connection dies mid-request), so re-check
                        // reachability shortly after to show the real outcome.
                        delay(5000)
                        testServerStatus()
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

    /**
     * Adds a new Ktor server with individual parameters.
     */
    fun addKtorServer(name: String, ipAddress: String, port: Int) {
        val newServer = KtorServerData(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            ipAddress = ipAddress,
            port = port
        )
        saveNewKtorServer(newServer)
    }

    fun deleteKtorServer(serverId: String) {
        viewModelScope.launch {
            try {
                dataStoreManager.removeKtorServer(serverId)
                val updatedServers = dataStoreManager.getKtorServers()
                val newSelectedServer =
                    if (updatedServers.isNotEmpty()) updatedServers[0] else SenderUiState().selectedKtorServer
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

        // Run status tests for the newly selected server
        testKtorServerStatus()
        testServerStatus()
    }

    fun clearLastOperationMessage() {
        uiState = uiState.copy(lastOperationMessage = null)
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
     * Updates the UI state with an error message.
     */
    private fun updateErrorState(message: String) {
        uiState = uiState.copy(errorMessage = message)
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

    val serverPingStatus: ServerStatus = ServerStatus.UNKNOWN,
    val serverSshStatus: ServerStatus = ServerStatus.UNKNOWN,
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
