package com.ajay.synccontacts.syncing

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.*
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import com.ajay.synccontacts.MainActivity
import com.ajay.synccontacts.model.Contact
//import com.ajay.synccontacts.network.ApiClient
import com.ajay.synccontacts.utils.Constants
import com.ajay.synccontacts.utils.ContactsManager

//import org.json.JSONArray
//import org.json.JSONObject

class SyncAdapter(context: Context, autoInitialize: Boolean) :
    AbstractThreadedSyncAdapter(context, autoInitialize) {

    private val TAG: String = javaClass.simpleName
    private var mContactsList: ArrayList<Contact> = ArrayList()


    init {
        mContactsList = getContactData()
        Log.i(TAG, "SyncAdapter Created: mContactsList = $mContactsList")
    }

    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?,
    ) {
        Log.i(TAG, "SyncAdapter called")

        /**
         * {@see SyncAdapter#serverNumberList} represents the numbers registered on server
         * Please be careful with string formatting of numbers on your Device and Server
         * My demo server uses the formatting as shown in {@see SyncAdapter#dummyServerResponseList}
         * Formatting on my device is like "(888) 899-9900" for the number 8888999900
         * So first I remove the commas, dash and spaces and then compare
         *
         * If you don't want to use a server
         * You can replace comment out the region Server response and uncomment the region Dummy response.
         * But again please be careful to check formatting of number on your device and
         * in {@see SyncAdapter#dummyServerResponseList}. If all goes well all numbers on the device
         * that match with the list will be synced.
         */

        //region Dummy response
        mContactsList = getContactData()
        var myType = ""
        var myNumber = ""
        for (contact in mContactsList) {
            for (number in contact.numbers) {
                if (isNumberAlreadyRegistered(number)) {

                    val dataCursor: Cursor = context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
                        "${ContactsContract.CommonDataKinds.Phone.TYPE} == ${ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE} AND ${ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID} = ${contact.id}",
                        null, null
                    ) ?: return
                    if (dataCursor.moveToFirst()) {
                        do {
                            myNumber = dataCursor.getString(0)
                            myType = dataCursor.getString(1)
                        } while (dataCursor.moveToNext())
                        dataCursor.close()
                    }
                    println("MyNumber :$myNumber")
                    println("mytype: $myType")
                    if (myNumber != number) {
                        ContactsManager.deleteNumber(context,number)
                    }

                    if (myType != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE.toString()) {
                        ContactsManager.deleteNumber(context, number)
                        /* context.contentResolver.delete(
                             ContactsContract.RawContacts.CONTENT_URI,
                             ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?",
                             arrayOf(Constants.ACCOUNT_TYPE)
                         )*/
                    }
                } else {
                    // If number is not registered and valid on server, register it
                    val cursor:Cursor = context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.DATA1),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ${contact.id}",
                        null, null
                    ) ?: return
                    while (cursor.moveToNext()) {
                        val dataNumber = cursor.getString(0)
                        ContactsManager.deleteNumber(context, dataNumber)
                        println("data1 $dataNumber and id is ${contact.id}")
                    }
                    ContactsManager.registerNumber(
                        context,
                        number,
                        contact.name,
                        contact.rawContactIdMap
                    )
                }
            }
        }

        // send broadcast response for manual refresh request
        context.sendBroadcast(Intent(MainActivity.ACTION_SYNC_COMPLETED))
        //endregion

        //region Server response
//        try {
//            val response = ApiClient.getClient().getNumbersList().execute()
//            if(response.isSuccessful && response.body() != null) {
//                val jsonArray = JSONArray(response.body()!!.string())
//
//                for(i in 0 until jsonArray.length()) {
//                    val jsonObject = JSONObject(jsonArray.get(i).toString())
//                    serverNumberList.add(jsonObject.get("number").toString())
//                }
//
//                for(contact in mContactsList) {
//                    for(number in contact.numbers) {
//                        if(isNumberAlreadyRegistered(number)) {
//                            // If number is registered and invalid on server, delete it
//                            if(!serverNumberList.contains(getFormattedNumber(number))) {
//                                ContactsManager.deleteNumber(context, number)
//                            }
//                        } else {
//                            // If number is not registered and valid on server, register it
//                            if(serverNumberList.contains(getFormattedNumber(number))) {
//                                ContactsManager.registerNumber(context, number, contact.name, contact.rawContactIdMap)
//                            }
//                        }
//                    }
//                }
//
//                // send broadcast response for manual refresh request
//                context.sendBroadcast(Intent(MainActivity.ACTION_SYNC_COMPLETED))
//            } else {
//                Log.d(TAG, "Network Error: ${response.errorBody()!!.string()}")
//            }
//
//            // Log the read contacts
//            for(contact in mContactsList) {
//                Log.e(TAG, "Contact details -> " +
//                        "Id: ${contact.id},Name: ${contact.name},Numbers: ${contact.numbers}")
//
//                // this will log raw contacts with data
//                logRawContacts(contact.id)
//            }
//        } catch (exception: Exception) {
//            Log.d(TAG, "Network Failure: ${exception.cause}")
//        }
        //endregion
    }

    /**
     * Method to get database number string formatted to match server string
     */
    private fun getFormattedNumber(numberString: String): String {
        return numberString
            .replace("+", "")
            .replace("(", "")
            .replace(")", "")
            .replace("-", "")
            .replace(" ", "")
    }

    /**
     * Method to log rawcontacts
     */
    private fun logRawContacts(contactId: String) {
        for ((index, rawContactId) in getRawContactIds(contactId).withIndex()) {
            Log.e(TAG, "RawContactId#$index -> $rawContactId")

            val dataCursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3
                ),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(rawContactId), null
            )

            if (dataCursor != null && dataCursor.moveToFirst()) {
                do {
                    val data =
                        dataCursor.getString(dataCursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1))
                    val data2 =
                        dataCursor.getString(dataCursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2))
                    val data3 =
                        dataCursor.getString(dataCursor.getColumnIndexOrThrow(ContactsContract.Data.DATA3))
                    Log.e(TAG, "Data1 -> $data")
                    Log.e(TAG, "Data2 -> $data2")
                    Log.e(TAG, "Data3 -> $data3")
                } while (dataCursor.moveToNext())
                dataCursor.close()
            }
        }
    }

    /**
     * Method to check if number is already registered
     */
    private fun isNumberAlreadyRegistered(number: String): Boolean {
        var isRegistered = false

        //region Get RawContactId's
        val rawContactIdCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
            arrayOf(number), null
        )

        val rawContactIdList = ArrayList<String>()
        if (rawContactIdCursor != null && rawContactIdCursor.moveToFirst()) {
            do {
                rawContactIdList.add(
                    rawContactIdCursor.getString(
                        rawContactIdCursor
                            .getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                    )
                )
            } while (rawContactIdCursor.moveToNext())
            rawContactIdCursor.close()
        }
        //endregion

        //region Check Account type
        for (rawContactId in rawContactIdList) {
            val accTypeCursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId), null
            )

            val accTypeList = ArrayList<String>()
            if (accTypeCursor != null && accTypeCursor.moveToFirst()) {
                do {
                    accTypeList.add(
                        accTypeCursor.getString(
                            accTypeCursor
                                .getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
                        )
                    )
                } while (accTypeCursor.moveToNext())
                accTypeCursor.close()
            }

            if (accTypeList.contains(Constants.ACCOUNT_TYPE)) {
                isRegistered = true
                break
            }
        }
        //endregion

        Log.d(TAG, "isNumberAlreadyRegistered($number) = $isRegistered")
        return isRegistered
    }

    /**
     * Method to get RawContactId list for the ContactId
     */
    private fun getRawContactIds(contactId: String): ArrayList<String> {
        val rawContactIds = ArrayList<String>()
        val rawContactsCursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )

        if (rawContactsCursor != null && rawContactsCursor.moveToFirst()) {
            do {
                rawContactIds.add(
                    rawContactsCursor.getString(
                        rawContactsCursor
                            .getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                    )
                )
            } while (rawContactsCursor.moveToNext())

            rawContactsCursor.close()
        }

        Log.d(TAG, "getRawContactIds($contactId) = $rawContactIds")
        return rawContactIds
    }

    /**
     * Method to get all contact data
     */
    private fun getContactData(): ArrayList<Contact> {
        val contacts = ArrayList<Contact>()

        // Get all contact id's
        val idCursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID), null, null, null, null
        )

        // create contactid list from idCursor
        if (idCursor != null && idCursor.moveToFirst()) {
            do {
                // add the id
                val contactId =
                    idCursor.getString(idCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))

                //region Numbers
                // query for all numbers for that id
                val numberCursor = context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                            "${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    null
                )

                // create the numbers list from numberCursor
                val numbers = ArrayList<String>()
                if (numberCursor != null && numberCursor.moveToFirst()) {
                    do {
                        numbers.add(
                            numberCursor.getString(
                                numberCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            )
                        )
                    } while (numberCursor.moveToNext())

                    numberCursor.close()
                }
                //endregion

                //region Names
                val nameCursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId), null
                )

                var name = ""
                if (nameCursor != null && nameCursor.moveToFirst()) {
                    do {
                        name = nameCursor.getString(
                            nameCursor
                                .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                        )
                    } while (nameCursor.moveToNext())

                    nameCursor.close()
                }
                //endregion

                //region RawContactIdMap
                val rawContactIdMap = HashMap<String, String>()
                for (number in numbers) {
                    val rawContactIdCursor = context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                        arrayOf(number), null
                    )

                    if (rawContactIdCursor != null && rawContactIdCursor.moveToFirst()) {
                        val rawContactId = rawContactIdCursor.getString(
                            rawContactIdCursor.getColumnIndexOrThrow(
                                ContactsContract.Data.RAW_CONTACT_ID
                            )
                        )
                        rawContactIdMap[number] = rawContactId
                        rawContactIdCursor.close()
                    }
                }
                //endregion

                if (numbers.isNotEmpty()) {
                    contacts.add(Contact(contactId, name, numbers, rawContactIdMap))
                }
            } while (idCursor.moveToNext())

            idCursor.close()

        }

        Log.d(TAG, "getContactData() = $contacts")
        return contacts
    }
}