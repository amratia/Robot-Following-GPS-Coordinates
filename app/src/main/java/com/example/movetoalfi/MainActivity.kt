package com.example.movetoalfi

import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.abs
import kotlin.math.atan
//import android.widget.ImageView
//import android.view.animation.Animation
//import android.view.animation.RotateAnimation

class MainActivity : AppCompatActivity(), SensorEventListener {

    var alfiHallLong = 31.501513
    var alfiHallLat = 30.018418
    var phoneAngle = 0f
    var bearingAngleToPoint = 0f
    private var SensorManage: SensorManager? = null
    var btnLocation: Button? = null
    var mgr: LocationManager? = null
    var listener: MyLocationListener? = null
    var permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    //-------------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SensorManage = getSystemService(SENSOR_SERVICE) as SensorManager
        mgr = getSystemService(LOCATION_SERVICE) as LocationManager
        listener = MyLocationListener()
        btnLocation = findViewById(R.id.btnLocation)
        with(btnLocation) {
            this?.setOnClickListener(View.OnClickListener {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    ActivityCompat.requestPermissions(this@MainActivity, permissions, _requestCode)
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                } else {
                    mgr!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, listener!!)
                }

            })
        }
    }
    //---------------------------------------------------------------------------------------------------
    override fun onPause() {
        super.onPause()
        // to stop the listener and save battery
        SensorManage!!.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        // code for system's orientation sensor registered listeners
        SensorManage!!.registerListener(
            this, SensorManage!!.getDefaultSensor(Sensor.TYPE_ORIENTATION),
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        // get angle around the z-axis rotated
        val degree = Math.round(event.values[0]).toFloat()
        phoneAngle = degree
        val tv3 = findViewById<View>(R.id.phoneAngleID) as TextView
        tv3.text = "Phone Angle: $degree"
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // not in use
    }
    //-------------------------------------------------------------------------------------------------

    inner class MyLocationListener : LocationListener {
        override fun onLocationChanged(location: Location) {
            //mgr.removeUpdates(listener);
            val tv1 = findViewById<View>(R.id.phoneLocationID) as TextView
            tv1.text = """
                Longitude:${location.longitude}
                Latitude:${location.latitude}
                """.trimIndent()

            if(alfiHallLat > location.latitude){ //1 and 4
                bearingAngleToPoint = Math.toDegrees(atan((alfiHallLong - location.longitude)/(alfiHallLat - location.latitude)))
                    .toFloat()
            }
            if(alfiHallLat < location.latitude){ //2 and 3
                bearingAngleToPoint = 180 + Math.toDegrees(atan((alfiHallLong - location.longitude)/(alfiHallLat - location.latitude)))
                    .toFloat()
            }

            if(bearingAngleToPoint < 0){
                bearingAngleToPoint = bearingAngleToPoint + 360
            }
            if(bearingAngleToPoint > 360){
                bearingAngleToPoint = bearingAngleToPoint - 360
            }

            val tv2 = findViewById<View>(R.id.bearingAngleID) as TextView
            tv2.text = "Bearing Angle: $bearingAngleToPoint"

            if(bearingAngleToPoint > (phoneAngle+10)){
                // Move Right
                funcSend(3)
            }
            if((bearingAngleToPoint+10) < phoneAngle){
                // Move Left
                funcSend(1)
            }
            if(abs(bearingAngleToPoint - phoneAngle) < 10){
                // move forward if phone away from location
                if((abs(alfiHallLat - location.latitude)<0.0001)&&(abs(alfiHallLong - location.longitude)<0.0001)){
                    // Move Forward
                    funcSend(5)
                }
                else{
                    // Stop
                    funcSend(8)
                }
            }
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>?,
            grantResults: IntArray?
        ) {
            when (requestCode) {
                _requestCode -> if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        permissions!!, _requestCode
                    )
                } else {
                    mgr!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, listener!!)
                }
            }
        }
    }

    companion object {
        const val _requestCode = 0
    }

    //-------------------------------------------------------------------------------------------------
    private fun funcSend(int: Int) {
        // Find all available drivers from attached devices.
        val manager = getSystemService(AppCompatActivity.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            return
        }
        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
            ?: // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return

        val port = driver.ports[0] // Most devices have just one port (port 0)
        fun Int.to2ByteArray() : ByteArray = byteArrayOf(toByte(), shr(8).toByte())

        port.open(connection)
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.write(int.to2ByteArray(), 10)
    }

    fun stopProgram(view: View) {finish();}
}

//if(location.getLongitude() < 31.5015){ // West of Alfi Hall
//    //moveForward
//    funcSend(5);
//}
//if(location.getLongitude() > 31.5015){ // East of Alfi hall
//    //STOP
//    funcSend(8);
//}