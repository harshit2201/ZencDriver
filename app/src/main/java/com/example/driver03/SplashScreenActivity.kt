package com.example.driver03

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import com.example.driver03.Model.DriverInfoModel
import com.example.driver03.Utils.UserUtils
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.iid.FirebaseInstanceId
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private const val LOGIN_REQUEST_CODE = 22
    }

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null) {
            firebaseAuth.removeAuthStateListener(listener)
        }
        super.onStop()
    }

    @SuppressLint("CheckResult")
    private fun delaySplashScreen() {
        Completable.timer(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).subscribe {
            firebaseAuth.addAuthStateListener(listener)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        providers = arrayListOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myAuth ->
            val user = myAuth.currentUser
            if (user != null) {

                // Update Token
                FirebaseInstanceId.getInstance()
                    .instanceId
                    .addOnFailureListener { e -> Toast.makeText(this@SplashScreenActivity, e.message,
                    Toast.LENGTH_LONG).show() }
                    .addOnSuccessListener { instanceIdResult ->

                        Log.d("TOKEN", instanceIdResult.token)
                        UserUtils.updateToken(this@SplashScreenActivity, instanceIdResult.token)
                    }
                checkUserFromFirebase()
            } else {
                showLoginLayout()
            }
        }
    }

    private fun checkUserFromFirebase() {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
//                        Toast.makeText(this@SplashScreenActivity, "User already registered!"
//                            , Toast.LENGTH_SHORT).show()
                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)
                    } else {
                        showRegisterLayout()
                    }
                }
            })
    }

    private fun goToHomeActivity(model: DriverInfoModel?) {
        Common.currentUser = model
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)
        val progress_bar: ProgressBar = findViewById(R.id.progress_bar)

        val edit_first_name = itemView.findViewById<View>(R.id.edit_first_name) as TextInputEditText
        val edit_last_name = itemView.findViewById<View>(R.id.edit_last_name) as TextInputEditText
        val edit_phone_number = itemView.findViewById<View>(R.id.edit_phone_number) as TextInputEditText

        val btn_continue = itemView.findViewById<View>(R.id.btn_register) as Button

        // Set Data
        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null &&
            TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber)
        )
            edit_phone_number.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)

        // View
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        // Event
        btn_continue.setOnClickListener {
            if (TextUtils.isDigitsOnly(edit_first_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity, "Please Enter First Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edit_last_name.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity, "Please Enter Last Name",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edit_phone_number.text.toString())) {
                Toast.makeText(
                    this@SplashScreenActivity, "Please Enter Phone Number",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else {
                val model = DriverInfoModel()
                model.firstName = edit_first_name.text.toString()
                model.lastName = edit_last_name.text.toString()
                model.phoneNumber = edit_phone_number.text.toString()
                model.rating = 0.0

                driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this@SplashScreenActivity, "" + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        progress_bar.visibility = View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@SplashScreenActivity, "Registered Successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()

                        goToHomeActivity(model)

                        progress_bar.visibility = View.GONE
                    }
            }
        }
    }


    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in).setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setAvailableProviders(providers).setTheme(R.style.LoginTheme)
                .setIsSmartLockEnabled(false)
                .build(),
            LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else
                Toast.makeText(
                    this@SplashScreenActivity,
                    "" + response!!.error!!.message,
                    Toast.LENGTH_SHORT
                ).show()
        }
    }

}




