# Wake on WAN

Wake on WAN is an Android application that allows you to remotely wake up or shut down your PC from anywhere outside your home network. It uses Wake-on-LAN (WOL) technology and SSH commands to manage a PC on the same network as a receiver phone. The app consists of two parts: a Sender phone and a Receiver phone.

## Features

- Wake your PC remotely: Sends a Wake-on-LAN magic packet to wake up a PC on your home network.
- Shut down your PC remotely: Sends an SSH command to shut down the PC.
- Works from anywhere: You can send commands from a phone that is not on the same local network as your PC.
- Background service: Uses a background service with a Ktor backend to receive commands.
- Port forwarding support: Leverages router port forwarding to communicate between the sender phone and the receiver phone.

## How It Works

1. Receiver Phone Setup:

    - An old phone is connected to the same local network as the PC. This acts as the receiver phone.
    - The necessary details (PC’s MAC address, IP address, username, and password) are entered into the app.
    - The receiver phone runs a Ktor backend service in the background (started in the app), listening for HTTP requests.

2. Sender Phone Setup:

    - The sender phone can be anywhere outside the home network.
    - The sender sends an HTTP request to the router’s public IP address (with a port forwarding rule in place).
    - The router forwards this request to the receiver phone’s Ktor backend.

3. Wake on LAN:

    - Upon receiving the request, the receiver phone sends a magic packet (WOL packet) to wake up the PC.

4. PC Shutdown:

    - The receiver phone logs into the PC using SSH and sends the shutdown command to power down the machine.

## Installation

1. Download and install the Wake on WAN app on both the Sender and Receiver phones.

2. Set up the Receiver phone by connecting it to the same local network as your PC and entering the necessary PC information.

3. Set up port forwarding on your router so that requests from the sender phone can reach the receiver phone.

## Usage

### Waking Up Your PC

1. Open the Wake on WAN app on the Sender phone.

2. Select your home network's public IP address or router's IP address.

3. Send a wake request. The Receiver phone will send a magic packet to wake up your PC.

### Shutting Down Your PC

1. Open the Wake on WAN app on the Sender phone.

2. Send a shutdown request. The Receiver phone will log into the PC via SSH and execute the shutdown command.

## Requirements

- Receiver Phone: An old Android phone connected to the same local network as the PC.

- Sender Phone: Any Android phone with the app installed.

- PC: Must support Wake-on-LAN and be configured for SSH access.
- Ktor Backend: The app uses Ktor to run a server on the receiver phone.

- Port Forwarding: Your router must be configured to forward a specific port to the receiver phone.

## Technologies Used

- Kotlin
- ComposeUI: For building the user interface.
- Ktor: For backend server functionality.
- Wake-on-LAN
- SSH: For remote shutdown commands.
