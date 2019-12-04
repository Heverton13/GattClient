package com.example.gattclient

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    var REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 200

    val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"


    companion object {
        private val TAG = "BLE-GUIA"
        val BLUETOOTH_REQUEST_CODE = 1
    }

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    var mBluetoothGatt: BluetoothGatt?= null
    val mHandler:Handler = Handler()
    lateinit var  mLampSwitcher: Switch
    private var connectionState = STATE_DISCONNECTED


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mLampSwitcher = lamp_state_switcher

        checkLocationPermission()

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            fuckMarshMallow()
        }

    }

    override fun onResume() {
        super.onResume()
        if (!mBluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, BLUETOOTH_REQUEST_CODE)
        }else{
            startBleScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == BLUETOOTH_REQUEST_CODE){
            if(resultCode == Activity.RESULT_OK){
                startBleScan()
            }else{
                Toast.makeText(this,"This application requires bluetooth",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBleScan(){

        val scanFilter  = ScanFilter.Builder()
            .build()

        val scanFilters:MutableList<ScanFilter> = mutableListOf()
        scanFilters.add(scanFilter)

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.d(TAG,"starting BLE scan")
        mBluetoothAdapter.bluetoothLeScanner.startScan(
            scanFilters,
            scanSettings,
            mBleScanCallbacks
        )
    }
    private fun stopBleScan(){
        mBluetoothAdapter.bluetoothLeScanner.stopScan(mBleScanCallbacks)
    }

    var bluetoothDevice:BluetoothDevice? = null

    fun processScanResult(scanResult:ScanResult){
        bluetoothDevice = scanResult.device
        Log.d(TAG, "device name ${bluetoothDevice!!.name} with address ${bluetoothDevice!!.address}")
        stopBleScan()
        lamp_state_switcher.setOnCheckedChangeListener { _: CompoundButton, state: Boolean ->
            Log.d(TAG, "changing the lamp state")
            setLampState(state)
            connect(bluetoothDevice!!.address)
        }


    }

    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }
        // Previously connected device.  Try to reconnect.
        if (bluetoothDevice!!.address != null && address == bluetoothDevice!!.address
            && mBluetoothGatt != null
        ) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            if (mBluetoothGatt!!.connect()) {
                DeviceProfile.getStateDescription(STATE_CONNECTING)
                return true
            } else {
                return false
            }
        }
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = bluetoothDevice!!.connectGatt(this,false,mBleGattCallBack)
        Log.d(TAG, "Trying to create a new connection.")
        DeviceProfile.getStateDescription(STATE_CONNECTING)
        return true
    }

    private val mBleScanCallbacks: ScanCallback by lazy {
        object : ScanCallback(){
            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG,"on Scan Failed")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                Log.d(TAG,"on Scan result")
                processScanResult(result!!)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                Log.d(TAG,"on batch scan results")
                results?.forEach {
                    processScanResult(it)
                }
            }
        }
    }

    private val mBleGattCallBack: BluetoothGattCallback by lazy {
        object : BluetoothGattCallback(){


            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)


                val intentAction: String
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        intentAction = ACTION_GATT_CONNECTED
                        DeviceProfile.getStatusDescription(STATE_CONNECTED)
                        broadcastUpdate(intentAction)
                        Log.i(TAG, "Connected to GATT server.")
                        Log.i(TAG, "Attempting to start service discovery: " +
                                mBluetoothGatt!!.discoverServices())
                    }
                }


                Log.d(TAG,"on Connection state change:" +
                        "      STATE:${DeviceProfile.getStateDescription(newState)}"+
                        "STATUS=${DeviceProfile.getStateDescription(status)}"    )

                if(newState == BluetoothProfile.STATE_CONNECTED){
                    mBluetoothGatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                Log.d(TAG,"on Services discovered")

                val characteristic = gatt?.getService(DeviceProfile.SERVICE_UUID)
                    ?.getCharacteristic(DeviceProfile.CHARACTERISTIC_STATE_UUID)

                gatt?.readCharacteristic(characteristic)

            }

            override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                Log.d(TAG,"onCharacteristicRead: reading into the characteristic ${characteristic?.uuid} the value ${characteristic?.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32,0)}")

                if(DeviceProfile.CHARACTERISTIC_STATE_UUID == characteristic?.uuid){

                    gatt?.setCharacteristicNotification(characteristic,true)

                    val value :Int= characteristic?.value!![0].toInt()
                    mHandler.post {
                        Log.i("Valor","Value: ${value}")
                        mLampSwitcher.setChecked(value==1)
                    }

                }

            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                super.onCharacteristicChanged(gatt, characteristic)
                Log.d(TAG,"onCharacteristicChanged: reading into the characteristic ${characteristic?.uuid} the value ${characteristic?.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32,0)}")

                val value :Int= characteristic?.value!![0].toInt()
                mHandler.post {
                    Log.d(TAG,"Value: ${value}")
                    mLampSwitcher.setChecked(value==1)
                }
            }

            private fun broadcastUpdate(action: String) {
                val intent = Intent(action)
                sendBroadcast(intent)
            }
        }
    }

    private fun setLampState(state:Boolean){
        val i:Byte = if (state) 1 else 0
    val newCharacteristicValue  = ByteBuffer.allocate(1)
            .put(i)
            .array()

        val characteristic = mBluetoothGatt?.getService(DeviceProfile.SERVICE_UUID)
            ?.getCharacteristic(DeviceProfile.CHARACTERISTIC_STATE_UUID)

        characteristic?.value = newCharacteristicValue
        mBluetoothGatt?.writeCharacteristic(characteristic)

    }
    fun checkLocationPermission() {
        Log.i("INFO", "Entro em check")
        if (isReadStorageAllowed()) {
            //If permission is already having then showing the toast
            Toast.makeText(this, "You already have the permission", Toast.LENGTH_LONG).show()
            //Existing the method with return
            startBleScan()
            return
        }
        //If the app has not the permission then asking for the permission
    }

    fun isReadStorageAllowed(): Boolean {
        //Getting the permission status
        Log.i("INFO", "Entro Read")
        var result: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        var result2: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED) {
            Log.i("INFO", "Permitiu")
            return true
        }
        //If permission is not granted returning false
        return false
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS -> {
                val perms = HashMap<String, Int>()
                // Initial
                perms[Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED

                // Fill with results
                for (i in permissions.indices)
                    perms[permissions[i]] = grantResults[i]

                // Check for ACCESS_FINE_LOCATION
                if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted

                    // Permission Denied
                    Toast.makeText(
                        this@MainActivity,
                        "All Permission GRANTED !! Thank You :)",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    // Permission Denied
                    Toast.makeText(
                        this@MainActivity,
                        "One or More Permissions are DENIED Exiting App :(",
                        Toast.LENGTH_SHORT
                    )
                        .show()

                    finish()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun fuckMarshMallow() {
        val permissionsNeeded = ArrayList<String>()

        val permissionsList = ArrayList<String>()
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location")

        if (permissionsList.size > 0) {
            if (permissionsNeeded.size > 0) {

                // Need Rationale
                var message = "App need access to " + permissionsNeeded[0]

                for (i in 1 until permissionsNeeded.size)
                    message = message + ", " + permissionsNeeded[i]

                showMessageOKCancel(message,
                    DialogInterface.OnClickListener { dialog, which ->
                        requestPermissions(
                            permissionsList.toTypedArray(),
                            REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
                        )
                    })
                return
            }
            requestPermissions(
                permissionsList.toTypedArray(),
                REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
            )
            return
        }

        Toast.makeText(
            this@MainActivity,
            "No new Permission Required- Launching App .You are Awesome!!",
            Toast.LENGTH_SHORT
        )
            .show()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun addPermission(permissionsList: MutableList<String>, permission: String): Boolean {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission)
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false
        }
        return true
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this@MainActivity)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

}

