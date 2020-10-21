package com.example.calendarremind

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import com.tencent.mmkv.MMKV
import java.util.*


object CalendarHelper {
    const val REQUEST_CALENDAR = 0
    const val EVENT_TITLE = "超话签到提醒"
    private const val EVENT_DESC = "连续签到当“超Fun”，你今天签到了么？快去看看吧\n一键签到链接"
    private const val TIME_OF_DAY = 1000 * 60 * 60 * 24
    private const val CALENDAR_URL = "content://com.android.calendar/calendars"
    private const val CALENDAR_EVENT_URL = "content://com.android.calendar/events"
    private const val CALENDAR_REMINDER_URL = "content://com.android.calendar/reminders"

    @SuppressLint("SetTextI18n")
    fun openTimePicker(context: Context, time1: Int, time2: Int, switch: SwitchCompat?, remindLayout: ViewGroup, isNew: Boolean) {
        var hour = time1
        var minute = time2
        if (hour == 0 && minute == 0) {
            hour = Calendar.getInstance().get(Calendar.HOUR)
            minute = Calendar.getInstance().get(Calendar.MINUTE)
        }
        val dialog = TimePickerDialog(context, TimePickerDialog.THEME_HOLO_LIGHT, { _, hourOfDay, minute ->
                //判断是否添加成功
                val isSuccess = if (isNew) {
                    addCalendarEvent(context, getTimeFromTimePicker(hourOfDay, minute))
                } else {
                    updateCalendarEvent(context, getTimeFromTimePicker(hourOfDay, minute))
                }
                if (isSuccess) {
                    val sHour = if (hourOfDay < 10) {
                        "0$hourOfDay"
                    } else {
                        hourOfDay.toString()
                    }
                    val sMinute = if (minute < 10) {
                        "0$minute"
                    } else {
                        minute.toString()
                    }
                    val remindTime = "$sHour:$sMinute"
                    (remindLayout.getChildAt(1) as TextView).text = remindTime
                    remindLayout.visibility = View.VISIBLE
                    val kv = MMKV.defaultMMKV()
                    kv.encode("calendar_remind", true)
                    kv.encode("remind_time", remindTime)
                    Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show()
                } else {
                    if (switch != null) {
                        remindLayout.visibility = View.GONE
                        switch.isChecked = !switch.isChecked
                    }
                    Toast.makeText(context, "设置失败", Toast.LENGTH_SHORT).show()
                }
            }, hour, minute, true
        )
        dialog.setOnCancelListener {
            if (switch != null) {
                remindLayout.visibility = View.GONE
                switch.isChecked = !switch.isChecked
            }
        }
        dialog.show()
    }

    fun fetchPermission(context: Context): Int {
        return if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            checkAndAddCalendarAccount(context)
        } else {
            ActivityCompat.requestPermissions(
                context as Activity, arrayOf(
                    Manifest.permission.WRITE_CALENDAR,
                    Manifest.permission.READ_CALENDAR
                ), REQUEST_CALENDAR
            )
            -1
        }
    }

    /**
     * 检查是否已经添加了日历账户，如果没有添加在先添加一个日历账户再次进行查询
     * 获取账户成功返回账户id，否则返回-1
     */
    fun checkAndAddCalendarAccount(context: Context): Int {
        val oldId = checkCalendarAccount(context)
        return if (oldId >= 0) {
            oldId
        } else {
            val addId = addCalendarAccount(context)
            if (addId >= 0) {
                checkCalendarAccount(context)
            } else {
                -1
            }
        }
    }

    private fun checkCalendarAccount(context: Context): Int {
        val userCursor = context.contentResolver.query(Uri.parse(CALENDAR_URL), null, null, null, null)
        try {
            if (userCursor == null) {
                return -1
            }
            val count = userCursor.count
            return if (count > 0) {
                // 存在账户，取第一个账户的id
                userCursor.moveToFirst()
                userCursor.getInt(userCursor.getColumnIndex(CalendarContract.Calendars._ID))
            } else {
                -1
            }
        } finally {
            userCursor?.close()
        }
    }

    private fun addCalendarAccount(context: Context): Long {
        val calendarName = "微博超话"
        val calendarAccountName = "wbsupergroup@sina.com"
        val calendarAccountType = "com.sina.wbsupergroup"
        val timeZone = TimeZone.getDefault()
        val value = ContentValues()
        value.put(CalendarContract.Calendars.NAME, calendarName)
        value.put(CalendarContract.Calendars.ACCOUNT_NAME, calendarAccountName)
        value.put(CalendarContract.Calendars.ACCOUNT_TYPE, calendarAccountType)
        value.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendarName)
        value.put(CalendarContract.Calendars.VISIBLE, 1)
        value.put(CalendarContract.Calendars.CALENDAR_COLOR, Color.YELLOW)
        value.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
        value.put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        value.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, timeZone.id)
        value.put(CalendarContract.Calendars.OWNER_ACCOUNT, calendarAccountName)
        value.put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0)
        var calendarUri = Uri.parse(CALENDAR_URL)
        calendarUri = calendarUri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, calendarAccountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, calendarAccountType)
            .build()
        val result = context.contentResolver.insert(calendarUri, value)
        return if (result == null) {
            -1
        } else {
            ContentUris.parseId(result)
        }
    }

    private fun addCalendarEvent(context: Context, beginTimeMillis: Long): Boolean {
        var beginTime = beginTimeMillis
        if (beginTime == 0L) {
            beginTime = Calendar.getInstance().timeInMillis + TIME_OF_DAY
        }
        try {
            val eventValues = ContentValues()
            eventValues.put(CalendarContract.Events.DTSTART, beginTime)
            eventValues.put(CalendarContract.Events.DURATION, "P10M") // 持续10分钟
            eventValues.put(CalendarContract.Events.TITLE, EVENT_TITLE)
            eventValues.put(CalendarContract.Events.DESCRIPTION, EVENT_DESC)
            eventValues.put(CalendarContract.Events.CALENDAR_ID, 1)
            eventValues.put(CalendarContract.Events.RRULE, "FREQ=DAILY")
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            val eUri = context.contentResolver.insert(Uri.parse(CALENDAR_EVENT_URL), eventValues)
            val eventId = ContentUris.parseId(eUri!!)
            if (eventId == 0L) {
                return false
            }
            MMKV.defaultMMKV().encode("event_id", eventId)
            // 插入提醒 - 依赖插入日程成功
            val reminderValues = ContentValues()
            reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventId)
            reminderValues.put(CalendarContract.Reminders.MINUTES, 0) // 提前0分钟提醒
            reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            val rUri = context.contentResolver.insert(Uri.parse(CALENDAR_REMINDER_URL), reminderValues)
            if (rUri == null || ContentUris.parseId(rUri) == 0L) {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun updateCalendarEvent(context: Context, beginTimeMillis: Long): Boolean {
        return try {
            val eventValues = ContentValues()
            eventValues.put(CalendarContract.Events.DTSTART, beginTimeMillis)
            eventValues.put(CalendarContract.Events.DURATION, "P10M")
            eventValues.put(CalendarContract.Events.RRULE, "FREQ=DAILY")
            val eventId = MMKV.defaultMMKV().decodeLong("event_id")
            val updateUri = ContentUris.withAppendedId(Uri.parse(CALENDAR_EVENT_URL), eventId)
            val rowNum = context.contentResolver.update(updateUri, eventValues, null, null)
            if (rowNum <= 0) {
                // 更新event不成功，说明用户在日历中删除了提醒事件，重新添加
                addCalendarEvent(context, beginTimeMillis)
            } else {
                // 更新提醒 - 依赖更新日程成功
                val reminderValues = ContentValues()
                reminderValues.put(CalendarContract.Reminders.MINUTES, 0)
                val rUri = Uri.parse(CALENDAR_REMINDER_URL)
                context.contentResolver.update(rUri, reminderValues, CalendarContract.Reminders.EVENT_ID + "= ?", arrayOf(java.lang.String.valueOf(eventId)))
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteCalendarEvent(context: Context, title: String) {
        val eventCursor = context.contentResolver.query(Uri.parse(CALENDAR_EVENT_URL), null, null, null, null)
        try {
            if (eventCursor == null) {
                return
            }
            if (eventCursor.count > 0) {
                eventCursor.moveToFirst()
                while (!eventCursor.isAfterLast) {
                    val eventTitle = eventCursor.getString(eventCursor.getColumnIndex("title"))
                    if (!TextUtils.isEmpty(title) && title == eventTitle) {
                        val id = eventCursor.getInt(eventCursor.getColumnIndex(CalendarContract.Calendars._ID)) // 取得id
                        val deleteUri = ContentUris.withAppendedId(Uri.parse(CALENDAR_EVENT_URL), id.toLong())
                        val rows = context.contentResolver.delete(deleteUri, null, null)
                        if (rows == -1) { // 事件删除失败
                            return
                        }
                    }
                    eventCursor.moveToNext()
                }
            }
        } finally {
            eventCursor?.close()
        }
    }

    /**
     * @return 明天零点的时间戳
     */
    private fun tomorrowZeroTime() = System.currentTimeMillis() / TIME_OF_DAY * TIME_OF_DAY + TIME_OF_DAY - TimeZone.getDefault().rawOffset

    private fun getTimeFromTimePicker(hour: Int, minute: Int) = tomorrowZeroTime() + (hour * 60 * 60 * 1000) + (minute * 60 * 1000)
}