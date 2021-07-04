package com.example.driver03.ui.Routes

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.driver03.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_routes.*


class RoutesFragment : Fragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment


        val root = inflater.inflate(R.layout.fragment_routes, container, false)

        Log.d("TAG", "GOKUVEGETA")

//        fab.setOnClickListener {view->
//            Snackbar.make(view, "Add routes", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
//        }

     return root
    }
}