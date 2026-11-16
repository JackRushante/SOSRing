package com.lorenzomarci.sosring

import android.Manifest
import android.app.Notification
import android.app.Person
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Finds the phone numbers an SMS notification belongs to, without keeping them: they are only
 * compared with the VIP list. SMS apps expose the sender in different places: Google Messages
 * and QKSMS-based apps (/e/OS Message) put a tel: or contact URI on the people, Fossify keeps the
 * number as the person key, AOSP-style apps show the number or the contact name as the title.
 */
object SmsVipResolver {

    fun candidateNumbers(
        context: Context,
        notification: Notification,
        rawMessages: Array<out Parcelable>
    ): Set<String> {
        val uris = linkedSetOf<String>()
        val numbers = linkedSetOf<String>()
        val names = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages)
                    .mapNotNull { it.senderPerson }
                    .forEach { collect(it, uris, numbers, names) }
            } catch (_: Exception) {
            }
        }
        people(notification).forEach { collect(it, uris, numbers, names) }
        val extras = notification.extras
        listOf(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE)
        ).forEach { field ->
            val number = VipMessageNotificationPolicy.phoneNumberIn(field)
            when {
                number != null -> numbers.add(number)
                !field.isNullOrBlank() -> names.add(field.toString().trim())
            }
        }
        uris.flatMapTo(numbers) { numbersForUri(context, it) }
        if (numbers.isEmpty()) names.flatMapTo(numbers) { numbersForContactName(context, it) }
        return numbers
    }

    fun uniqueVip(numbers: Set<String>, contacts: List<VipContact>): VipContact? {
        val matches = contacts.filter { contact ->
            numbers.any { candidate -> PhoneUtils.matches(candidate, contact.number) }
        }.distinctBy { PhoneUtils.normalize(it.number) }
        return matches.singleOrNull()
    }

    private fun collect(
        person: Person,
        uris: MutableSet<String>,
        numbers: MutableSet<String>,
        names: MutableSet<String>
    ) {
        person.uri?.let(uris::add)
        VipMessageNotificationPolicy.phoneNumberIn(person.key)?.let(numbers::add)
        val name = person.name
        val number = VipMessageNotificationPolicy.phoneNumberIn(name)
        when {
            number != null -> numbers.add(number)
            !name.isNullOrBlank() -> names.add(name.toString().trim())
        }
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

    private fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun numbersForContactUri(context: Context, uri: Uri): Set<String> {
        if (!hasContactsPermission(context)) return emptySet()
        return try {
            val contactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, uri) ?: return emptySet()
            phoneNumbers(
                context,
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(ContentUris.parseId(contactUri).toString())
            )
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Numbers of the address-book contacts whose display name is exactly the notification title. */
    private fun numbersForContactName(context: Context, name: String): Set<String> {
        if (!hasContactsPermission(context) || name.length > MAX_NAME_LENGTH) return emptySet()
        return try {
            phoneNumbers(context, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(name))
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun phoneNumbers(context: Context, selection: String, args: Array<String>): Set<String> {
        val numbers = linkedSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            selection,
            args,
            null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                cursor.getString(numberIndex)?.takeIf { it.isNotBlank() }?.let(numbers::add)
            }
        }
        return numbers
    }

    private const val MAX_NAME_LENGTH = 100
}
