package com.emanuel.gymhelper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions_rationale)
        findViewById<android.view.View>(R.id.closeButton).setOnClickListener { finish() }
    }
}
