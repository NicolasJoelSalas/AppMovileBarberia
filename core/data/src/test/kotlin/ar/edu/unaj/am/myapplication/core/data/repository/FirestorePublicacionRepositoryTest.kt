package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.data.images.PreparadorImagenTrabajo
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.network.CloudinaryApi
import ar.edu.unaj.am.myapplication.core.network.ImagenSubidaDto
import com.google.android.gms.tasks.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.io.IOException
import java.util.concurrent.Executor
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody

class FirestorePublicacionRepositoryTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val user = mock(FirebaseUser::class.java)
    private val api = mock(CloudinaryApi::class.java)
    private val preparador = mock(PreparadorImagenTrabajo::class.java)
    private fun repo(cloud: String = "barberia", preset: String = "trabajos") =
        FirestorePublicacionRepository(preparador, api, cloud, preset, firestore, auth)
    private val nueva = NuevaPublicacion("solicitud", "content://foto", "Corte")
    private fun presetMatcher(): RequestBody = ArgumentMatchers.any(RequestBody::class.java) ?: "".toRequestBody()
    private fun fileMatcher(): MultipartBody.Part = ArgumentMatchers.any(MultipartBody.Part::class.java)
        ?: MultipartBody.Part.createFormData("file", "foto.jpg", byteArrayOf().toRequestBody())

    @Suppress("UNCHECKED_CAST")
    private fun <T> task(result: T?, failure: Exception? = null): Task<T> {
        val task = mock(Task::class.java) as Task<T>
        `when`(task.isSuccessful).thenReturn(failure == null)
        `when`(task.result).thenReturn(result)
        `when`(task.exception).thenReturn(failure)
        `when`(task.addOnCompleteListener(ArgumentMatchers.any(Executor::class.java),
            ArgumentMatchers.any<OnCompleteListener<T>>())).thenAnswer {
            it.getArgument<OnCompleteListener<T>>(1).onComplete(task); task
        }
        return task
    }
    private fun perfil(habilitado: Boolean) {
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("barbero")
        val collection = mock(CollectionReference::class.java)
        val reference = mock(DocumentReference::class.java)
        val snapshot = mock(DocumentSnapshot::class.java)
        `when`(firestore.collection("barberos")).thenReturn(collection)
        `when`(collection.document("barbero")).thenReturn(reference)
        `when`(snapshot.getBoolean("Estado")).thenReturn(habilitado)
        val loaded = task(snapshot)
        `when`(reference.get(Source.SERVER)).thenReturn(loaded)
    }
    @Test fun `no authenticated user means no upload or database access`() = runTest {
        assertTrue(repo().publicar(nueva) is AppResult.Error)
        verifyNoInteractions(api, preparador, firestore)
    }
    @Test fun `client or disabled barber cannot upload`() = runTest {
        perfil(false)
        assertTrue(repo().publicar(nueva) is AppResult.Error)
        verifyNoInteractions(api, preparador)
    }
    @Test fun `missing Cloudinary config reports error before decoding or uploading`() = runTest {
        perfil(true)
        val result = repo("", "").publicar(nueva)
        assertTrue(result is AppResult.Error)
        verifyNoInteractions(api, preparador)
    }
    @Test fun `retry after Firestore failure reuses uploaded image`() = runTest {
        perfil(true)
        val collection = mock(CollectionReference::class.java)
        val reference = mock(DocumentReference::class.java)
        val snapshot = mock(DocumentSnapshot::class.java)
        `when`(firestore.collection("publicaciones")).thenReturn(collection)
        `when`(collection.document("solicitud")).thenReturn(reference)
        `when`(snapshot.exists()).thenReturn(false)
        val loaded = task(snapshot)
        `when`(reference.get(Source.SERVER)).thenReturn(loaded)
        `when`(preparador.preparar("content://foto", 0f, 0f)).thenReturn(byteArrayOf(1, 2, 3))
        `when`(api.subir(ArgumentMatchers.eq("barberia") ?: "barberia", presetMatcher(), fileMatcher()))
            .thenReturn(ImagenSubidaDto("https://res.cloudinary.com/barberia/image/upload/v1/foto.jpg", "foto"))
        val failed = task<Any>(null, IOException("Sin conexión"))
        `when`(firestore.runTransaction(ArgumentMatchers.any<Transaction.Function<Any>>()))
            .thenReturn(failed)
        val repository = repo()
        assertTrue(repository.publicar(nueva) is AppResult.Error)
        assertTrue(repository.publicar(nueva) is AppResult.Error)
        verify(preparador, times(1)).preparar("content://foto", 0f, 0f)
        verify(api, times(1)).subir(ArgumentMatchers.eq("barberia") ?: "barberia", presetMatcher(), fileMatcher())
    }
}
