package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class QuoteItem(
    val id: Int,
    val text: String
)

@Composable
fun QuotesScreen(
    quotes: List<QuoteItem>,
    error: String? = null,

    onCreateClick: () -> Unit = {},

    onDeleteModeClick: () -> Unit = {},

    onTagsClick: () -> Unit = {},

    onEditClick: (Int) -> Unit = { _ -> },

    onDeleteClick: (Int) -> Unit = { _ -> },

    onSearchChange: (String) -> Unit = { _ -> }

) {

    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)

    ) {

        // TOP ACTION BUTTONS

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = onCreateClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Create")
            }

            Button(
                onClick = onDeleteModeClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Delete Multiple")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SEARCH + TAGS

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(

                value = "",

                onValueChange = onSearchChange,

                modifier = Modifier.weight(1f),

                singleLine = true,

                label = {
                    Text("Search")
                }
            )

            Button(
                onClick = onTagsClick,
                modifier = Modifier.fillMaxHeight()
            ) {
                Text("Tags")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QUOTES LIST

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (quotes.isEmpty() && error == null) {
            Text("No quotes yet. Create your first quote.")
        }

        LazyColumn(

            modifier = Modifier.fillMaxSize(),

            verticalArrangement = Arrangement.spacedBy(8.dp)

        ) {

            items(quotes, key = { it.id }) { quote ->

                QuoteCard(

                    quote = quote,

                    onEditClick = {
                        onEditClick(quote.id)
                    },

                    onDeleteClick = {
                        onDeleteClick(quote.id)
                    }
                )
            }
        }
    }
}

@Composable
fun QuoteCard(

    quote: QuoteItem,

    onEditClick: () -> Unit,

    onDeleteClick: () -> Unit

) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = quote.text,

                modifier = Modifier.weight(1f),

                maxLines = 1,

                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = onEditClick
            ) {
                Text("Edit")
            }

            Button(
                onClick = onDeleteClick
            ) {
                Text("Delete")
            }
        }
    }
}

