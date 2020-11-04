package com.example.kotlintestsuiteapi24

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun startScenarioOne(view : View) {
        val intent = Intent(this, TestScenario1Activity::class.java)
        startActivity(intent)
        }

}