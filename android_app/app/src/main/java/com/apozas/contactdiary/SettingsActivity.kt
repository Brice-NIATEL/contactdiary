package com.apozas.contactdiary

/*
    This file is part of Contact Diary.
    Contact Diary is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    Contact Diary is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with Contact Diary. If not, see <http://www.gnu.org/licenses/>.
    Copyright 2020 by Alex Pozas-Kerstjens (apozas)
*/

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.Cursor.*
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.apozas.contactdiary.ContactDatabase.Companion.SQL_CREATE_ENTRIES
import com.apozas.contactdiary.ContactDatabase.Companion.SQL_DELETE_ENTRIES
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat


class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)
        supportFragmentManager.beginTransaction().replace(R.id.container, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val preferences = preferenceManager.sharedPreferences
            val prefsedit = preferences.edit()

            val oldTime = preferences.getString("reminder_time", "21:00").toString()
            val reminderTime = findPreference<EditTextPreference>("reminder_time")
            val reminderToggle =
                findPreference<SwitchPreference>("reminder_toggle") as SwitchPreference

            reminderTime?.setOnPreferenceChangeListener { _, newValue ->
                var isTimeGood = true
                val newTime = newValue as String
                if (newTime.split(":").size == 2) {
                    val timeparts = newValue.split(":")
                    if ((timeparts[0].toInt() > 23) || (timeparts[1].toInt() > 59)) {
                        Toast.makeText(
                            context,
                            getString(R.string.incorrect_alarm_time),
                            Toast.LENGTH_LONG
                        ).show()
                        isTimeGood = false
                    }
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.incorrect_alarm_time),
                        Toast.LENGTH_LONG
                    ).show()
                    isTimeGood = false
                }
                if ((newValue.toString() != oldTime) && isTimeGood) {
                    prefsedit.putString("reminder_time", newValue)
                    prefsedit.apply()
                    Toast.makeText(context, getString(R.string.alarm_modified), Toast.LENGTH_SHORT)
                        .show()
                    updateNotificationPreferences(reminderToggle.isEnabled)
                    true
                } else {
                    prefsedit.putString("reminder_time", oldTime)
                    prefsedit.apply()
                    false
                }
            }
            reminderToggle.setOnPreferenceChangeListener { _, newValue ->
                updateNotificationPreferences(newValue as Boolean)
                true
            }

            val prefTheme = findPreference<ListPreference>("theme")
            prefTheme!!.setOnPreferenceChangeListener { _, newValue ->
                when (newValue) {
                    "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                true
            }

            val export = findPreference<Preference>("export")
            export!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                exportDB(requireContext())
                true
            }

            val import = findPreference<Preference>("import")
            import!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                importDB(requireContext())
                true
            }
        }

        private fun updateNotificationPreferences(on: Boolean) {
            val receiver = ComponentName(
                requireActivity().applicationContext, NotificationReceiver::class.java
            )
            val pm = requireActivity().applicationContext.packageManager
            val notificationHandler = NotificationHandler()
            if (on) {
                notificationHandler.scheduleNotification(requireActivity().applicationContext)
                pm.setComponentEnabledSetting(
                    receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                notificationHandler.disableNotification(requireActivity().applicationContext)
                pm.setComponentEnabledSetting(
                    receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        private fun exportDB(context: Context) {
            val dbHelper = FeedReaderDbHelper(context)
            val exportDir = File(context.getExternalFilesDir(null), "")
            val dateFormatter = SimpleDateFormat("yyyy-LL-dd-HH:mm")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val file = File(exportDir, "ContactDiary_database.csv")
            try {
                file.createNewFile()
                val csvWrite = CSVWriter(FileWriter(file))
                val db: SQLiteDatabase = dbHelper.readableDatabase
                val cursor: Cursor = db.rawQuery(
                    "SELECT * FROM ${ContactDatabase.ContactDatabase.FeedEntry.TABLE_NAME}",
                    null
                )
                val columnNames =
                    cursor.columnNames.drop(1).toMutableList()    // We don't care of the _id column
                columnNames[columnNames.indexOf("CloseContact")] = "DistanceKept"
                csvWrite.writeNext(columnNames.toTypedArray())
                while (cursor.moveToNext()) {
                    //Which column you want to export
                    val columns = cursor.columnCount
                    val arrStr = mutableListOf<String>()
                    for (i in 1 until columns) {    // We don't care of the _id column
                        when (columnNames[i - 1]) {
                            "BeginTime" -> arrStr.add(dateFormatter.format(cursor.getLong(i)))
                            "EndTime" -> arrStr.add(dateFormatter.format(cursor.getLong(i)))
                            "Relative" -> arrStr.add(
                                when (cursor.getInt(i)) {
                                    -1 -> ""
                                    0 -> ""
                                    1 -> "Yes"
                                    3 -> "No"
                                    else -> cursor.getInt(i).toString()
                                }
                            )
                            "EncounterType" -> arrStr.add(
                                when (cursor.getInt(i)) {
                                    -1 -> ""
                                    1 -> "Indoors"
                                    3 -> "Outdoors"
                                    else -> cursor.getInt(i).toString()
                                }
                            )
                            "DistanceKept" -> arrStr.add(
                                when (cursor.getInt(i)) {
                                    -1 -> ""
                                    1 -> "Yes"
                                    3 -> "No"
                                    5 -> "Unsure"
                                    else -> cursor.getInt(i).toString()
                                }
                            )
                            else -> when (cursor.getType(i)) {
                                FIELD_TYPE_STRING -> arrStr.add(cursor.getString(i))
                                FIELD_TYPE_INTEGER -> arrStr.add(cursor.getLong(i).toString())
                                FIELD_TYPE_NULL -> arrStr.add("")
                            }
                        }
                    }
                    csvWrite.writeNext(arrStr.toList().toTypedArray())
                }
                csvWrite.close()
                cursor.close()
                Toast.makeText(context, "Exported to $exportDir", Toast.LENGTH_LONG).show()
            } catch (sqlEx: Exception) {
                Log.e("Export", sqlEx.message, sqlEx)
            }
        }

        private fun importDB(context: Context) {
            val feedEntry = ContactDatabase.ContactDatabase.FeedEntry
            val dbHelper = FeedReaderDbHelper(context)
            val db = dbHelper.writableDatabase
            val importDir = File(context.getExternalFilesDir(null), "")
            val file = File(importDir, "ContactDiary_database.csv")
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HH:mm")
            try {
                db.execSQL(SQL_DELETE_ENTRIES)
                db.execSQL(SQL_CREATE_ENTRIES)
                val reader = CSVReader(FileReader(file))
                reader.readNext()    // Skip first line (contains column names)
                var nextLine = reader.readNext()
                while (nextLine != null) {
                    val type = nextLine[0]
                    val name = nextLine[1]
                    val place = nextLine[2]
                    val beginTime = nextLine[3]
                    val endTime = nextLine[4]
                    val phone = nextLine[5]
                    val relative = nextLine[6]
                    val companions = nextLine[7]
                    val encounterType = nextLine[8]
                    val distance = nextLine[9]
                    val notes = nextLine[10]

                    val values = ContentValues().apply {
                        put(feedEntry.TYPE_COLUMN, type)
                        put(feedEntry.NAME_COLUMN, name)
                        put(feedEntry.PLACE_COLUMN, place)
                        put(feedEntry.TIME_BEGIN_COLUMN, dateFormatter.parse(beginTime).time)
                        put(feedEntry.TIME_END_COLUMN, dateFormatter.parse(endTime).time)
                        put(feedEntry.PHONE_COLUMN, phone)
                        put(feedEntry.RELATIVE_COLUMN, when (relative) {
                            "Yes" -> 1
                            "No" -> 3
                            else -> -1
                        })
                        put(feedEntry.CLOSECONTACT_COLUMN, when (distance) {
                            "Yes" -> 1
                            "No" -> 3
                            "Unsure" -> 5
                            else -> -1
                        })
                        put(feedEntry.ENCOUNTER_COLUMN, when (encounterType) {
                            "Indoors" -> 1
                            "Outdoors" -> 3
                            else -> -1
                        })
                        put(feedEntry.COMPANIONS_COLUMN, companions)
                        put(feedEntry.NOTES_COLUMN, notes)
                    }
                    db?.insert(feedEntry.TABLE_NAME, null, values)
                    nextLine = reader.readNext()
                }
                Toast.makeText(context, "Database imported", Toast.LENGTH_LONG).show()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                Toast.makeText(context, "The specified file was not found", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}
