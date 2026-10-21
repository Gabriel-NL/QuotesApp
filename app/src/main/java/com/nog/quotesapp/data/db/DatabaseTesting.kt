package com.nog.quotesapp.data.db

import android.content.Context
import android.util.Log
import androidx.core.app.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nog.quotesapp.data.entity.QuoteEntity
import kotlinx.coroutines.launch

fun ComponentActivity.testDatabase(
    context: Context,
    lifecycleOwner: LifecycleOwner
)
{
    val db = DatabaseProvider.getDatabase(context)

    lifecycleOwner.lifecycleScope.launch {

        db.quoteDao().insert(
            QuoteEntity(
                text = "Test quote"
            )
        )

        val quotes =
            db.quoteDao().getAll()

        Log.d(
            "ROOM_TEST",
            quotes.toString()
        )
    }
}