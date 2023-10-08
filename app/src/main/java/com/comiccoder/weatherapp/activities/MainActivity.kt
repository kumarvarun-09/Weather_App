package com.comiccoder.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.comiccoder.weatherapp.R
import com.comiccoder.weatherapp.constants.Constants
import com.comiccoder.weatherapp.databinding.ActivityMainBinding
import com.comiccoder.weatherapp.models.WeatherResponse
import com.comiccoder.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var progressBar: Dialog? = null
    private  lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()

        if (isLocationEnabled()) {
            refresh()
        } else {
            Toast.makeText(this, "Please turn on location", Toast.LENGTH_LONG).show()
        }

        binding?.swipeRefresh?.setOnRefreshListener {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            if (isLocationEnabled()) {
                refresh()
                binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun refresh() {
        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    showRationaleDialogForPermission()
                }

            }).onSameThread().check()
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            showProgressDialog()

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        Log.i("RESPONSE RESULT", weatherList.toString())

                    }
                    setupUI()
                    hideProgressDialog()
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("RESPONSE ERROR", t.message.toString())
                }

            })

        } else {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val a1 = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val a2 = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return a1 || a2
    }

    private fun showRationaleDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("Permission required")
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
            val latitude = mLastLocation!!.latitude
            val longitude = mLastLocation.longitude
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun showProgressDialog() {
        if (progressBar == null) {
            progressBar = Dialog(this)
            progressBar!!.setContentView(R.layout.custom_waiting_dialog)
        }
        progressBar!!.show()
    }

    private fun hideProgressDialog() {
        if (progressBar != null) {
            progressBar!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if(weatherResponseJsonString!!.isEmpty()){
            return
        }

        val weatherList:WeatherResponse = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

        for (i in weatherList.weather.indices) {
            val weather = weatherList.weather[i]
            binding?.tvMain?.text = weather.main
            binding?.tvMainDescription?.text = weather.description

            when (weather.icon) {
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "50d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)


            }
        }
        var unit: String? = "°C"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unit = getUnit(application.resources.configuration.locales.toString())
        }
        binding?.tvTemp?.text =
            weatherList.main.temp.toString() + unit

        binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
        binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
        binding?.tvHumidity?.text = weatherList.main.humidity.toString() + "%"
        binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
        binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"
        binding?.tvSpeed?.text = weatherList.wind.speed.toString()
        binding?.tvName?.text = weatherList.name
        binding?.tvCountry?.text = weatherList.sys.country

    }

    private fun getUnit(v: String?): String? {
        var value = "°C"
        if (v == "US" || v == "LR" || v == "MM") {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}