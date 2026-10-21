package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier)
{
    val searchText =
        remember { mutableStateOf("") }

    val quoteOfTheDay =
        remember {
            mutableStateOf(
                "The cake is a lie"
            )
        }

    Box(
        modifier =
            Modifier
                .padding(16.dp)
    )
    {
        androidx.compose.foundation.layout.Column(
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        )
        {
            QuoteForToday(
                quote = quoteOfTheDay.value,
                onReroll =
                    {
                        // TODO
                    }
            )

            OutlinedTextField(
                value = searchText.value,
                onValueChange =
                    {
                        searchText.value = it
                    },
                modifier =
                    Modifier.fillMaxWidth(),
                label =
                    {
                        Text("Search")
                    },
                singleLine = true
            )

            Text(
                text =
                    "Results will appear here"
            )
        }
    }
}

@Composable
private fun QuoteForToday(
    quote: String,
    onReroll: () -> Unit
)
{
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp),
        verticalAlignment =
            Alignment.CenterVertically
    )
    {
        Card(
            modifier =
                Modifier
                    .weight(1f)
                    .height(64.dp)
        )
        {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(12.dp),
                contentAlignment =
                    Alignment.CenterStart
            )
            {
                Text(quote)
            }
        }

        Button(
            onClick = onReroll,
            modifier =
                Modifier.height(64.dp)
        )
        {
            Text("🎲")
        }
    }
}