package com.example.calendarremind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import com.tencent.mmkv.MMKV
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        MMKV.initialize(this)
        val kv = MMKV.defaultMMKV()

        val switchStatus = kv.decodeBool("calendar_remind", false)
        calendarSwitch.isChecked = switchStatus
        if (switchStatus) {
            remindLayout.visibility = View.VISIBLE
            remindTime.text = kv.decodeString("remind_time", "00:00")
        }

        calendarSwitch.setOnCheckedChangeListener{ buttonView, isChecked ->
            if (CalendarHelper.fetchPermission(this) == -1) {
                buttonView.isChecked = !isChecked
            } else {
                if (isChecked) {
                    openTimePicker(calendarSwitch, true)
                } else {
                    remindLayout.visibility = View.GONE
                    CalendarHelper.deleteCalendarEvent(this, CalendarHelper.EVENT_TITLE)
                    kv.remove("calendar_remind")
                    kv.remove("remind_time")
                    kv.remove("event_id")
                }
            }
        }

        remindLayout.setOnClickListener{
            if (CalendarHelper.fetchPermission(this) != -1) {
                openTimePicker(null, false)
            }
        }
    }

    private fun openTimePicker(switch: SwitchCompat?, isNew: Boolean) {
        var hour: Int
        var minute: Int
        try {
            val time = remindTime.text.toString().split(":")
            hour = time[0].toInt()
            minute = time[1].toInt()
        } catch (e: Exception) {
            hour = 0
            minute = 0
        }
        CalendarHelper.openTimePicker(this, hour, minute, switch, remindLayout, isNew)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CalendarHelper.REQUEST_CALENDAR) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CalendarHelper.checkAndAddCalendarAccount(this)
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_CALENDAR)) {
                    startActivity(Intent(this, OpenPermissionGuide::class.java))
                }
            }
        }
    }
}