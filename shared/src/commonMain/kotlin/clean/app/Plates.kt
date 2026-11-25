package clean.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import clean.core.PlateKit
import clean.core.encodePlateKit
import clean.core.loadPlates
import clean.core.parsePlateKit
import clean.core.shortNumber
import clean.core.standardKit

/** The bar and plates kept for [unit] (kv `plates.kg` / `plates.lb`), else the standard ones. */
fun AppModel.plateKit(unit: String): PlateKit = parsePlateKit(options.value["plates.$unit"], unit)

/** What goes on each side of the bar to make [total], in words, with the kit's own bar and plates. */
fun plateText(total: Double, unit: String, kit: PlateKit): String {
    val load = loadPlates(total, kit) ?: return "That is less than the ${shortNumber(kit.bar)} $unit bar."
    val used = load.perSide.map { (plate, n) -> if (n == 1) shortNumber(plate) else "$n × ${shortNumber(plate)}" }
    return "A ${shortNumber(kit.bar)} $unit bar with, on each side: " + (if (used.isEmpty()) "nothing" else used.joinToString(", ")) + "." +
        if (load.leftOver > 0) " ${shortNumber(load.leftOver)} $unit of it cannot be made with your plates." else ""
}

/**
 * The bar and plates one has, for the plate calculator: the bar's weight and, for each size, how many plates in all (blank for as many as
 * needed, 0 for none), and a size of one's own. Saved for [unit]; "Standard" puts back the usual bar and plates.
 */
@Composable
fun PlateKitDialog(model: AppModel, unit: String, onDismiss: () -> Unit) {
    val stored by model.options.collectAsState()
    val kit = remember(stored, unit) { parsePlateKit(stored["plates.$unit"], unit) }
    var bar by remember(kit) { mutableStateOf(shortNumber(kit.bar)) }
    // Every size on offer: the standard ones and any of one's own, largest first; a size owned is filled with its count.
    val rows = remember(kit) {
        mutableStateListOf<Pair<Double, String>>().apply {
            val sizes = (standardKit(unit).plates.keys + kit.plates.keys).distinct().sortedDescending()
            addAll(sizes.map { size -> size to if (size !in kit.plates) "0" else kit.plates[size]?.toString().orEmpty() })
        }
    }
    var extra by remember { mutableStateOf("") }
    fun save() {
        val plates = rows.mapNotNull { (size, text) ->
            val count = text.trim().toIntOrNull()
            when {
                text.isBlank() -> size to null
                count == null || count <= 0 -> null
                else -> size to count
            }
        }.toMutableList()
        parseNumber(extra)?.takeIf { it > 0 && plates.none { p -> p.first == it } }?.let { plates += it to null }
        val next = PlateKit(parseNumber(bar)?.takeIf { it >= 0 } ?: kit.bar, plates.toMap())
        model.setOption("plates.$unit", encodePlateKit(next, unit))
        onDismiss()
    }
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bar and plates ($unit)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(bar, { bar = it }, label = { Text("Bar ($unit)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text("How many plates of each size you have in all. Leave a size blank for as many as needed, or put 0 if you have none.", style = MaterialTheme.typography.bodySmall)
                rows.forEachIndexed { i, (size, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        Text("${shortNumber(size)} $unit", Modifier.weight(1f))
                        OutlinedTextField(
                            text, { rows[i] = size to it.filter(Char::isDigit) }, placeholder = { Text("Any") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(extra, { extra = it }, label = { Text("Another size ($unit)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { model.setOption("plates.$unit", null); onDismiss() }) { Text("Standard") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
