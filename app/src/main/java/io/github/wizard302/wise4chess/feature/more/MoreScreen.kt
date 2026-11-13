package io.github.wizard302.wise4chess.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.core.ui.R as UiR

/** The More section: everything that is not playing or studying. */
@Composable
fun MoreScreen(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.nav_more),
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_title)) },
            leadingContent = { Icon(painterResource(UiR.drawable.ic_settings), contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSettings),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
