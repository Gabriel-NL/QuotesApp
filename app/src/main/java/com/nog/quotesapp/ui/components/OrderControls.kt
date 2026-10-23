package com.nog.quotesapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nog.quotesapp.model.EntryOrder

@Composable
fun OrderControls(order:EntryOrder,descending:Boolean,onOrder:(EntryOrder)->Unit,onDescending:(Boolean)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick={expanded=true}) { Text("Order by: "+if(order==EntryOrder.ALPHABETIC) "Alphabetic" else "Letter count") }
            DropdownMenu(expanded,onDismissRequest={expanded=false}) {
                DropdownMenuItem(text={Text("Alphabetic")},onClick={onOrder(EntryOrder.ALPHABETIC);expanded=false})
                DropdownMenuItem(text={Text("Letter count")},onClick={onOrder(EntryOrder.LETTER_COUNT);expanded=false})
            }
        }
        FilterChip(selected=descending,onClick={onDescending(!descending)},label={Text(if(descending) "Descending" else "Ascending")})
    }
}
