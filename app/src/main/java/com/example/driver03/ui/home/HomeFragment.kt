package com.example.driver03.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.driver03.Common
import com.example.driver03.R
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.android.synthetic.main.fragment_home.*
import java.io.IOException
import java.util.*

@Suppress("DEPRECATION")
class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var root_layout: FrameLayout

    // Location
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    // Online system
    private lateinit var onlineRef: DatabaseReference
    private var currentUserRef: DatabaseReference? = null
    private lateinit var driversLocationRef: DatabaseReference
    private lateinit var geoFIre: GeoFire

    private val onlineValueEventListener = object: ValueEventListener {
        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists() && currentUserRef != null)
                currentUserRef!!.onDisconnect().removeValue()
        }
    }

    override fun onDestroy() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback)
        geoFIre.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        root_layout = root!!.findViewById(R.id.root_layout) as FrameLayout
        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    private fun init() {

        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")


        // Permission check so that when we first login in our app we get to our location after allowing permission
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(root_layout, getString(R.string.permission_require), Snackbar.LENGTH_LONG).show()
            return
        }

        buildLocationRequest()
        buildLocationCallback()
        updateLocation()
    }

    private fun updateLocation() {
        if (fusedLocationProviderClient == null)
        {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Snackbar.make(root_layout, getString(R.string.permission_require), Snackbar.LENGTH_LONG).show()
                return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null)
        {
            locationCallback = object: LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)

                    val newPos = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                    val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                    val addressList: List<Address>?

                    try {
                        addressList = geoCoder.getFromLocation(locationResult.lastLocation.latitude,
                            locationResult.lastLocation.longitude, 1)
                        val cityName = addressList[0].locality

                        driversLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE)
                            .child(cityName)
                        currentUserRef = driversLocationRef.child(
                            FirebaseAuth.getInstance().currentUser!!.uid)

                        geoFIre = GeoFire(driversLocationRef)

                        // Update Location
                        geoFIre.setLocation(
                            FirebaseAuth.getInstance().currentUser!!.uid,
                            GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                        ){ key: String?, error: DatabaseError? ->
                            if (error != null)
                                Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()


                        }

                        registerOnlineSystem()

                    } catch (e:IOException)
                    {
                        Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun buildLocationRequest() {
        if (locationRequest == null)
        {
            locationRequest = LocationRequest()
            locationRequest!!.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest!!.setFastestInterval(15000)   // 15 sec
            locationRequest!!.interval = 10000  // 10 sec
            locationRequest!!.setSmallestDisplacement(50f)   // 50m
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap!!

        // Request permission
        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object: PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Snackbar.make(root_layout, getString(R.string.permission_require), Snackbar.LENGTH_LONG).show()
                        return
                    }


                    map.isMyLocationEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = true
                    map.setOnMyLocationButtonClickListener {
                        fusedLocationProviderClient!!.lastLocation
                            .addOnFailureListener { e ->
                                Toast.makeText(context!!, e.message, Toast.LENGTH_SHORT).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f))
                            }
                        true
                    }

                    // Layout Button
                    val locationButton = (mapFragment.requireView().findViewById<View>("1".toInt())!!.parent!! as View)
                        .findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 50



                    // Location
                    buildLocationRequest()
                    buildLocationCallback()
                    updateLocation()
                }



                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(requireContext(), "Permission "+p0!!.permissionName+" was denied", Toast.LENGTH_SHORT).show()
                }

            })
            .check()


        try {
            val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context
            , R.raw.uber_maps_style))
            if(!success)
                Log.e("GOKU_ERROR", "Style parsing error")
        } catch (e:Resources.NotFoundException) {
            Log.e("GOKU_ERROR", e.message.toString())
        }

        Snackbar.make(mapFragment.requireView(), "You're online!", Snackbar.LENGTH_SHORT).show()
    }
}