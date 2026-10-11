package ar.edu.unaj.am.myapplication.features.todos.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Todo
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.theme.MyApplicationTheme
import ar.edu.unaj.am.myapplication.features.todos.R

@Composable
fun TodoDetailRoute(
    viewModel: TodoDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navegación disparada por ESTADO: cuando isSaved pasa a true, volvemos.
    // La key hace que el efecto se relance solo cuando ese valor cambia.
    val isSaved = (uiState as? TodoDetailUiState.Success)?.isSaved == true
    LaunchedEffect(isSaved) {
        if (isSaved) onBack()
    }

    TodoDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onEditClick = viewModel::onEditClick,
        onCancelEdit = viewModel::onCancelEdit,
        onSave = viewModel::onSave,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onCompletedChange = viewModel::onCompletedChange,
        onUserMessageShown = viewModel::onUserMessageShown
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    uiState: TodoDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEditClick: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onUserMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val successState = uiState as? TodoDetailUiState.Success
    val isEditing = successState?.isEditing == true

    // Mientras editamos, el botón "atrás" del sistema cancela la edición en vez de salir.
    BackHandler(enabled = isEditing, onBack = onCancelEdit)

    val userMessageText = successState?.userMessage?.asMessage()
    LaunchedEffect(successState?.userMessage) {
        if (userMessageText != null) {
            snackbarHostState.showSnackbar(userMessageText)
            onUserMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (uiState) {
                                is TodoDetailUiState.Loading -> R.string.todo_detail_title_loading
                                is TodoDetailUiState.Error -> R.string.todo_detail_title_error
                                is TodoDetailUiState.Success ->
                                    if (uiState.isEditing) R.string.todo_detail_title_editing else R.string.todo_detail_title
                            }
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = if (isEditing) onCancelEdit else onBack) {
                        Text(stringResource(R.string.todo_detail_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (successState != null) {
                val canSave = successState.form?.isValid == true && !successState.isSaving
                FloatingActionButton(
                    onClick = {
                        if (!isEditing) onEditClick() else if (canSave) onSave()
                    },
                    containerColor = if (!isEditing || canSave) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                        contentDescription = stringResource(
                            if (isEditing) R.string.todo_detail_save else R.string.todo_detail_edit
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        val contentModifier = Modifier.padding(paddingValues)

        when (uiState) {
            is TodoDetailUiState.Loading -> LoadingContent(
                message = stringResource(R.string.todo_detail_loading),
                modifier = contentModifier
            )

            is TodoDetailUiState.Error -> ErrorContent(
                title = stringResource(R.string.todo_detail_error_title),
                message = uiState.error.asMessage(),
                onRetry = onRetry,
                modifier = contentModifier
            )

            is TodoDetailUiState.Success -> Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val form = uiState.form
                if (form != null) {
                    EditContent(
                        form = form,
                        isSaving = uiState.isSaving,
                        onTitleChange = onTitleChange,
                        onDescriptionChange = onDescriptionChange,
                        onCompletedChange = onCompletedChange
                    )
                } else {
                    ReadContent(todo = uiState.todo)
                }
            }
        }
    }
}

@Composable
private fun ReadContent(todo: Todo) {
    StatusCard(isCompleted = todo.isCompleted)

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = todo.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(text = todo.description, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StatusCard(isCompleted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(if (isCompleted) R.string.todo_detail_completed else R.string.todo_detail_pending),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EditContent(
    form: TodoForm,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCompletedChange: (Boolean) -> Unit
) {
    Column {
        OutlinedTextField(
            value = form.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.todo_detail_field_title)) },
            isError = !form.isValid,
            supportingText = if (!form.isValid) {
                { Text(stringResource(R.string.todo_detail_title_required)) }
            } else {
                null
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = form.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.todo_detail_field_description)) },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.todo_detail_completed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = form.isCompleted, onCheckedChange = onCompletedChange, enabled = !isSaving)
            }
        }

        AnimatedVisibility(visible = isSaving, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).size(32.dp))
        }
    }
}

// ---------- Previews ----------

private val previewTodo = Todo(1, "Estudiar ViewModel", "Separar la lógica de la UI con StateFlow", false)

@Composable
private fun TodoDetailPreview(uiState: TodoDetailUiState) {
    MyApplicationTheme(dynamicColor = false) {
        TodoDetailScreen(
            uiState = uiState,
            onBack = {},
            onRetry = {},
            onEditClick = {},
            onCancelEdit = {},
            onSave = {},
            onTitleChange = {},
            onDescriptionChange = {},
            onCompletedChange = {},
            onUserMessageShown = {}
        )
    }
}

@Preview(name = "Read", showBackground = true)
@Composable
private fun TodoDetailReadPreview() = TodoDetailPreview(TodoDetailUiState.Success(previewTodo))

@Preview(name = "Edit", showBackground = true)
@Composable
private fun TodoDetailEditPreview() = TodoDetailPreview(
    TodoDetailUiState.Success(
        todo = previewTodo,
        form = TodoForm(title = "", description = previewTodo.description, isCompleted = false)
    )
)

@Preview(name = "Error", showBackground = true)
@Composable
private fun TodoDetailErrorPreview() = TodoDetailPreview(TodoDetailUiState.Error(DomainError.Network))
