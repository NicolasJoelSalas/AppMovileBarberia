package ar.edu.unaj.am.myapplication.features.books.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Author
import ar.edu.unaj.am.myapplication.core.domain.model.Book
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.theme.MyApplicationTheme
import ar.edu.unaj.am.myapplication.features.books.R
import ar.edu.unaj.am.myapplication.features.books.components.BookCover
import ar.edu.unaj.am.myapplication.features.books.list.DownloadCount
import ar.edu.unaj.am.myapplication.features.books.list.previewBook
import java.util.Locale

@Composable
fun BookDetailRoute(
    viewModel: BookDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BookDetailScreen(uiState = uiState, onBack = onBack, onRetry = viewModel::onRetry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    uiState: BookDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.books_detail_title), fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.books_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        val contentModifier = Modifier.padding(paddingValues)

        when (uiState) {
            is BookDetailUiState.Loading -> LoadingContent(
                message = stringResource(R.string.books_detail_loading),
                modifier = contentModifier
            )

            is BookDetailUiState.Error -> ErrorContent(
                title = stringResource(R.string.books_detail_error_title),
                message = uiState.error.asMessage(),
                onRetry = onRetry,
                modifier = contentModifier
            )

            is BookDetailUiState.Success -> BookDetailContent(
                book = uiState.book,
                modifier = contentModifier
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailContent(
    book: Book,
    modifier: Modifier = Modifier
) {
    // LocalUriHandler: forma "Compose" de abrir un link en el navegador, sin tocar Context ni Intents.
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BookCover(
            url = book.coverUrl,
            contentDescription = book.title,
            modifier = Modifier.size(width = 160.dp, height = 240.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = book.authors.joinToString { it.displayWithYears() }
                .ifEmpty { stringResource(R.string.books_unknown_author) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        DownloadCount(count = book.downloadCount)

        if (book.languages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.books_languages, book.languages.joinToString { it.toLanguageName() }),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        book.readUrl?.let { url ->
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { uriHandler.openUri(url) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.books_read))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.books_summary))
            Text(
                text = book.summary ?: stringResource(R.string.books_no_summary),
                style = MaterialTheme.typography.bodyLarge
            )

            if (book.subjects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.books_subjects))
                // FlowRow: como un Row, pero salta de línea cuando no entra más.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.subjects.forEach { subject ->
                        SuggestionChip(onClick = {}, label = { Text(subject) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun Author.displayWithYears(): String =
    if (birthYear != null || deathYear != null) "$name (${birthYear ?: "?"}–${deathYear ?: "?"})" else name

/** "en" -> "English", "es" -> "español"… según el idioma del dispositivo. */
private fun String.toLanguageName(): String =
    Locale.forLanguageTag(this).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

// ---------- Previews ----------

@Composable
private fun BookDetailPreview(uiState: BookDetailUiState) {
    MyApplicationTheme(dynamicColor = false) {
        BookDetailScreen(uiState = uiState, onBack = {}, onRetry = {})
    }
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun BookDetailSuccessPreview() = BookDetailPreview(BookDetailUiState.Success(previewBook))

@Preview(name = "Error", showBackground = true)
@Composable
private fun BookDetailErrorPreview() = BookDetailPreview(BookDetailUiState.Error(DomainError.Network))
