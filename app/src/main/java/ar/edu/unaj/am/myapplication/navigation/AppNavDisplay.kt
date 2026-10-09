package ar.edu.unaj.am.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ar.edu.unaj.am.myapplication.di.AppContainer
import ar.edu.unaj.am.myapplication.features.books.detail.BookDetailRoute
import ar.edu.unaj.am.myapplication.features.books.detail.BookDetailViewModel
import ar.edu.unaj.am.myapplication.features.books.list.BookListRoute
import ar.edu.unaj.am.myapplication.features.books.list.BookListViewModel
import ar.edu.unaj.am.myapplication.features.playground.PlaygroundScreen
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnosListRoute
import ar.edu.unaj.am.myapplication.features.todos.detail.TodoDetailRoute
import ar.edu.unaj.am.myapplication.features.todos.detail.TodoDetailViewModel
import ar.edu.unaj.am.myapplication.features.todos.list.TodoListRoute
import ar.edu.unaj.am.myapplication.features.todos.list.TodoListViewModel

/**
 * La navegación vive en :app. Las features no se conocen entre sí:
 * solo exponen callbacks (onTodoClick, onBack…) y acá decidimos qué hace cada uno.
 */
@Composable
fun AppNavDisplay(
    navigationState: NavigationState,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    // entryProvider: "para esta key, mostrá esta pantalla". La key llega ya tipada:
    // key.todoId es un Int, sin getInt("todoId") ni strings de por medio.
    val entryProvider = entryProvider<NavKey> {

        entry<TurnosListDestination> {
            val viewModel = viewModel {
                ar.edu.unaj.am.myapplication.features.turnos.list.TurnoListViewModel(container.observeTurnos)
            }
            TurnosListRoute(viewModel = viewModel)
        }

        entry<TodoListDestination> {
            // viewModel { } crea el ViewModel UNA vez por entrada del back stack:
            // sobrevive a rotaciones y recomposiciones, y se destruye cuando la pantalla sale del stack.
            val viewModel = viewModel {
                TodoListViewModel(
                    observeTodos = container.observeTodos,
                    refreshTodos = container.refreshTodos,
                    addTodo = container.addTodo,
                    toggleTodo = container.toggleTodo,
                    deleteTodo = container.deleteTodo
                )
            }
            TodoListRoute(
                viewModel = viewModel,
                onTodoClick = { todoId -> navigationState.navigate(TodoDetailDestination(todoId)) }
            )
        }

        entry<TodoDetailDestination> { key ->
            val viewModel = viewModel {
                TodoDetailViewModel(
                    todoId = key.todoId,
                    getTodo = container.getTodo,
                    updateTodo = container.updateTodo
                )
            }
            TodoDetailRoute(viewModel = viewModel, onBack = { navigationState.goBackFrom(key) })
        }

        entry<BookListDestination> {
            val viewModel = viewModel { BookListViewModel(getBooks = container.getBooks) }
            BookListRoute(
                viewModel = viewModel,
                onBookClick = { bookId -> navigationState.navigate(BookDetailDestination(bookId)) }
            )
        }

        entry<BookDetailDestination> { key ->
            val viewModel = viewModel {
                BookDetailViewModel(bookId = key.bookId, getBook = container.getBook)
            }
            BookDetailRoute(viewModel = viewModel, onBack = { navigationState.goBackFrom(key) })
        }

        entry<PlaygroundDestination> {
            PlaygroundScreen()
        }
    }

    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = navigationState::goBack,
        modifier = modifier
    )
}

/**
 * Convierte cada back stack en entradas "decoradas" y junta las de los tabs visibles.
 *
 * Decoramos TODOS los back stacks, aunque no se vean: así, al cambiar de tab,
 * las pantallas del otro tab conservan su ViewModel y su estado (scroll, formularios…).
 */
@Composable
private fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val entriesByTab = backStacks.mapValues { (_, backStack) ->
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                // Guarda el estado de rememberSaveable de cada pantalla
                rememberSaveableStateHolderNavEntryDecorator(),
                // Le da a cada pantalla su propio ViewModelStore
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider
        )
    }
    return visibleTabs.flatMap { tab -> entriesByTab.getValue(tab) }
}
