package com.example.calendarremind

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import kotlinx.android.synthetic.main.activity_guide.*

class OpenPermissionGuide : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        gotoSettings.setOnClickListener{
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", this.packageName, null)
            startActivity(intent)
            finish()
        }
    }
}