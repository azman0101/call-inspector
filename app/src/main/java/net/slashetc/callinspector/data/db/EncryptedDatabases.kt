package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Opening of the app's private SQLite databases, plain (JVM tests) or SQLCipher-encrypted (the app). */
internal object EncryptedDatabases {

    fun open(
        context: Context,
        factory: SupportSQLiteOpenHelper.Factory,
        name: String,
        callback: SupportSQLiteOpenHelper.Callback,
    ): SupportSQLiteDatabase = factory.create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
    ).writableDatabase

    /**
     * Opens [name] with SQLCipher under its own random passphrase, wrapped by the Android Keystore key
     * [keyAlias] ([DatabaseKeyStore]). The passphrase is zeroed once the database is open.
     */
    fun openEncrypted(
        context: Context,
        name: String,
        keyAlias: String,
        wrappedPassphraseFile: String,
        callback: SupportSQLiteOpenHelper.Callback,
    ): SupportSQLiteDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyStore(context, keyAlias, wrappedPassphraseFile, name).getDatabasePassphrase()
        try {
            return open(context, SupportOpenHelperFactory(passphrase), name, callback)
        } finally {
            passphrase.fill(0)
        }
    }
}
