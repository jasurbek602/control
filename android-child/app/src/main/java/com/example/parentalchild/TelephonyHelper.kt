package com.example.parentalchild

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony

data class CallItem(val number: String, val type: String, val durationSec: Long, val date: Long)
data class SmsItem(val address: String, val body: String, val type: String, val date: Long)

class TelephonyHelper(private val context: Context) {

    // Qo'ng'iroqlar tarixini olish (oxirgi N ta)
    fun getCallLogs(limit: Int = 20): List<CallItem> {
        val list = mutableListOf<CallItem>()
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE),
            null, null, "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numCol = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val durCol = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val number = it.getString(numCol) ?: "Noma'lum"
                val typeInt = it.getInt(typeCol)
                val duration = it.getLong(durCol)
                val date = it.getLong(dateCol)

                val typeStr = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "Kiruvchi"
                    CallLog.Calls.OUTGOING_TYPE -> "Chiquvchi"
                    CallLog.Calls.MISSED_TYPE -> "O'tkazib yuborilgan"
                    else -> "Boshqa"
                }

                list.add(CallItem(number, typeStr, duration, date))
                count++
            }
        }
        return list
    }

    // SMS xabarlarni olish (oxirgi N ta)
    fun getSmsLogs(limit: Int = 20): List<SmsItem> {
        val list = mutableListOf<SmsItem>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE),
            null, null, "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addrCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
            val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
            val dateCol = it.getColumnIndex(Telephony.Sms.DATE)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val address = it.getString(addrCol) ?: "Noma'lum"
                val body = it.getString(bodyCol) ?: ""
                val typeInt = it.getInt(typeCol)
                val date = it.getLong(dateCol)

                val typeStr = if (typeInt == Telephony.Sms.MESSAGE_TYPE_INBOX) "Kiruvchi" else "Chiquvchi"

                list.add(SmsItem(address, body, typeStr, date))
                count++
            }
        }
        return list
    }
}
