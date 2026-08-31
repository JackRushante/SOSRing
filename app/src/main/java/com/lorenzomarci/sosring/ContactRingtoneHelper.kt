package com.lorenzomarci.sosring

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

object ContactRingtoneHelper {

    private const val TAG = "ContactRingtone"

    fun getRingtoneUri(context: Context, phoneNumber: String): Uri? {
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.CUSTOM_RINGTONE
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val customRingtone = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.CUSTOM_RINGTONE)
                    )
                    if (!customRingtone.isNullOrBlank()) {
                        Log.d(TAG, "Custom ringtone found for VIP contact")
                        return Uri.parse(customRingtone)
                    }
                }
            }
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS permission missing: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve ringtone for VIP contact: ${e.message}")
            null
        }
    }
}
