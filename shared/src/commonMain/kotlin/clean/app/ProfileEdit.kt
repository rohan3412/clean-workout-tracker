package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import clean.app.data.Profile
import clean.core.shortNumber

/**
 * Editing the profile and switching between profiles, opened from the Profile page ("Edit", Height, Switch profile). It used to be the
 * first page of Settings; since 1.1.0 Settings has no Profile heading.
 */
@Composable
fun ProfileEditDialog(model: AppModel, onClose: () -> Unit) {
    val profiles by model.profiles.collectAsState()
    val profile by model.profile.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    CleanDialog(
        onDismissRequest = onClose,
        title = { Text("Profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                profile?.let { p ->
                    var name by remember(p.id) { mutableStateOf(p.name) }
                    var height by remember(p.id) { mutableStateOf(p.heightCm?.let(::shortNumber) ?: "") }
                    OutlinedTextField(name, { name = it; if (it.isNotBlank()) model.update(p.copy(name = it.trim())) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        Text("Weights in")
                        Choice(listOf("kg", "lb"), p.unit, { it }) { model.update(p.copy(unit = it)) }
                    }
                    OutlinedTextField(
                        height, { height = it; model.update(p.copy(heightCm = parseNumber(it))) },
                        label = { Text("Height (cm)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Caption("Profiles")
                for (p in profiles) {
                    Row(Modifier.fillMaxWidth().clickable { model.switchTo(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.id == profile?.id, onClick = { model.switchTo(p.id) })
                        Text(p.name, Modifier.weight(1f))
                        if (profiles.size > 1) IconButton(onClick = { deleting = p }) { Icon(CleanIcons.Delete, "Delete profile") }
                    }
                }
                QuietButton("New profile", { adding = true }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
    if (adding) {
        var name by remember { mutableStateOf("") }
        CleanDialog(
            onDismissRequest = { adding = false },
            title = { Text("New profile") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.createProfile(name.trim()); adding = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { p ->
        Confirm("Delete ${p.name}?", "All of this profile's workouts and measurements are removed from this phone. Export first if you want to keep them.", "Delete", { model.deleteProfile(p.id) }) { deleting = null }
    }
}
