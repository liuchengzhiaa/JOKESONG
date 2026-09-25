package com.example.asmrplayer.core.storage

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.libraryPreferences by preferencesDataStore(name = "library_preferences")

class RootPreferences(private val context: Context) {
    val rootUri: Flow<Uri?> = context.libraryPreferences.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[RootUriKey]?.let(Uri::parse) }

    suspend fun setRootUri(uri: Uri) {
        context.libraryPreferences.edit { preferences ->
            preferences[RootUriKey] = uri.toString()
        }
    }

    private companion object {
        val RootUriKey = stringPreferencesKey("library_root_uri")
    }
}
