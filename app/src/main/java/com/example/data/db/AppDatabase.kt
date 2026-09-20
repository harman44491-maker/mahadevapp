package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CustomerRecord
import com.example.data.model.FieldType
import com.example.data.model.FormFieldDefinition
import com.example.data.model.SyncLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerRecord::class,
        FormFieldDefinition::class,
        SyncLogEntry::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun formFieldDao(): FormFieldDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer_records ADD COLUMN replyToCustomer TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customer_records ADD COLUMN documentsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "field_collect_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaultFields(database.formFieldDao())
                    }
                }
            }

            private suspend fun populateDefaultFields(formFieldDao: FormFieldDao) {
                val defaultFields = listOf(
                    FormFieldDefinition(
                        fieldKey = "annual_income",
                        label = "Annual Household Income",
                        fieldType = FieldType.NUMBER,
                        placeholder = "e.g. 150000",
                        isRequired = false,
                        displayOrder = 1,
                        isActive = true
                    ),
                    FormFieldDefinition(
                        fieldKey = "customer_category",
                        label = "Customer Category",
                        fieldType = FieldType.DROPDOWN,
                        optionsJson = "[\"Individual / Farmer\", \"Micro Enterprise\", \"Self Help Group (SHG)\", \"Cooperative Member\"]",
                        placeholder = "Select category",
                        isRequired = false,
                        displayOrder = 2,
                        isActive = true
                    ),
                    FormFieldDefinition(
                        fieldKey = "document_verified",
                        label = "Physical ID Verified On-Site",
                        fieldType = FieldType.CHECKBOX,
                        isRequired = false,
                        displayOrder = 3,
                        isActive = true
                    )
                )
                formFieldDao.insertDefaultFields(defaultFields)
            }
        }
    }
}
