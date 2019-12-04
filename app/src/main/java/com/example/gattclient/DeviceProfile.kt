package com.example.gattclient

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import java.util.*

class DeviceProfile {

    companion object {
        var SERVICE_UUID = UUID.fromString( "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

        //Read-Wrote only characteristic providing the state of the lamp
        var CHARACTERISTIC_STATE_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")


        fun getStateDescription(state: Int): String {
            return when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected"
                BluetoothProfile.STATE_CONNECTING ->  "Connecting"
                BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
                else -> "Unknown State $state"
            }
        }

        fun getStatusDescription(status: Int): String {
            return when (status) {
                BluetoothGatt.GATT_SUCCESS ->  "SUCCESS"
                else ->  "Unknown Status $status"
            }
        }

    }

}