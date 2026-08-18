package com.lorenzomarci.sosring

import android.Manifest
import android.app.Notification
import android.app.Person
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object GoogleMessagesVipResolver {

    fun candidateNumbers(
        context: Context,
        notification: Notification,
        rawMessages: Array<out Parcelable>
    ): Set<String> {
        val uris = linkedSetOf<String>()
        try {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages)
                .mapNotNullTo(uris) { it.senderPerson?.uri }
        } catch (_: Exception) {
        }
        people(notification).mapNotNullTo(uris) { it.uri }
        return uris.flatMapTo(linkedSetOf()) { numbersForUri(context, it) }
    }

    fun uniqueVip(numbers: Set<String>, contacts: List<VipContact>): VipContact? {
        val matches = contacts.filter { contact ->
            numbers.any { candidate -> PhoneUtils.matches(candidate, contact.number) }
        }.distinctBy { PhoneUtils.normalize(it.number) }
        return matches.singleOrNull()
    }

    @Suppress("DEPRECATION")
    private fun people(notification: Notification): List<Person> =
        notification.extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST).orEmpty()

    private fun numbersForUri(context: Context, rawUri: String): Set<String> {
        val uri = try {
            Uri.parse(rawUri)
        } catch (_: Exception) {
            return emptySet()
        }
        return when (uri.scheme?.lowercase()) {
            "tel" -> setOfNotNull(uri.schemeSpecificPart?.takeIf { it.isNotBlank() })
            "content" -> numbersForContactUri(context, uri)
            else -> emptySet()
        }
    }

    private fun numbersForContactUri(context: Context, uri: Uri): Set<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptySet()
        return try {
            val contactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, uri) ?: return emptySet()
            val contactId = ContentUris.parseId(contactUri).toString()
            val numbers = linkedSetOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    cursor.getString(numberIndex)?.takeIf { it.isNotBlank() }?.let(numbers::add)
                }
            }
            numbers
        } catch (_: Exception) {
            emptySet()
        }
    }
}
