# Wake on WAN Android App - Copilot Instructions

## Project Overview
This is a dual-phone Android app for remote PC wake/shutdown via Wake-on-LAN and SSH. It uses a **Receiver** phone on the local network running a Ktor server and a **Sender** phone that can be anywhere, communicating through router port forwarding.

## Key Architecture Components

### Core Services (`app/src/main/java/hu/krafcsikgergo/wakeonwan/services/`)
- **`KtorServerService.kt`**: Foreground service running embedded Ktor server on receiver phone
- **`WakeOnLANTask.kt`**: Sends magic packets (broadcasts to 255.255.255.255:9)
- **`SSHManager.kt`**: Uses JSch library for PC shutdown commands
- **`DataStoreManager.kt`**: Persistent configuration storage
- **`Data.kt`**: Global state objects (`ServerData`, `KtorServerData`)

### Screen Architecture
- **Navigation**: Single-activity with Compose Navigation between Sender/Receiver/Schedules
- **Screens**: `SenderScreen.kt` (HTTP client), `ReceiverScreen.kt` (server config), `SchedulesScreen.kt` (automation)
- **Composables**: Reusable form fields in `composables/` directory

### Network Communication
- **Client**: Ktor client with Android engine (replaces Retrofit for consistency)
- **Server**: Ktor with routes: `/wakeup`, `/shutdown`, `/test-server`, `/` (health check)
- **Port Forwarding**: External traffic → Router → Receiver phone's Ktor server

## Development Patterns

### Configuration Management
```kotlin
// Global state objects are populated from DataStore on app start
KtorServerData.ipAddress = DataStoreManager.getInstance(context).getString("ktorIpAddress")
ServerData.macAddress = DataStoreManager.getInstance(context).getString("macAddress")
```

### Service Integration
- Start server: `context.startService(Intent(context, KtorServerService::class.java))`
- Background operations use `kotlinx.coroutines` with `Dispatchers.IO`
- Foreground service notification keeps server running

### Networking Specifics
- Wake-on-LAN uses UDP broadcast packets (6 bytes 0xFF + 16x MAC address)
- SSH connections require `StrictHostKeyChecking=no` for automation
- MAC address parsing supports both `:` and `-` delimiters

## Build Configuration
- **Min SDK**: 30, **Target SDK**: 34, **Compile SDK**: 34
- **Key Dependencies**: Ktor (server + client), JSch (SSH), Compose, DataStore
- **Packaging**: Excludes Netty metadata to avoid conflicts
- **Release**: APK output in `app/release/` with keystore signing

## Testing Workflow
1. Configure receiver phone with PC details (IP, MAC, SSH credentials)
2. Start Ktor server service on receiver
3. Set up router port forwarding (external port → receiver phone IP:port)
4. Test from sender phone using router's public IP

## Security Considerations
- SSH passwords stored in DataStore (consider encryption)
- Network security config allows cleartext traffic
- Service runs with `connectedDevice` foreground service type