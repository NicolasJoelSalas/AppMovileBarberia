package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.data.images.PreparadorImagenTrabajo
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository
import ar.edu.unaj.am.myapplication.core.network.CloudinaryApi
import ar.edu.unaj.am.myapplication.core.network.ImagenSubidaDto
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestorePublicacionRepository(
    private val preparador: PreparadorImagenTrabajo,
    private val api: CloudinaryApi,
    private val cloudName: String,
    private val uploadPreset: String,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val local: PublicacionesClienteLocal? = null,
    private val sesionLocal: SesionClienteLocal? = null
) : PublicacionRepository {
    // Reutiliza la foto subida si Firestore falla y el usuario reintenta la misma publicación.
    private val pendientes = ConcurrentHashMap<String, ImagenSubidaDto>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observar() = flow {
        val uid = auth.currentUser?.uid
        if (uid != null && local != null && sesionLocal != null) {
            emitAll(sesionLocal.observarValidacion(uid).flatMapLatest { cliente ->
                if (cliente) local.observar(uid, observarRemotos(soloServidor = true))
                else observarRemotos()
            })
        } else emitAll(observarRemotos())
    }

    private fun observarRemotos(soloServidor: Boolean = false) = callbackFlow<AppResult<List<Publicacion>>> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(AppResult.Error(DomainError.Known(ErrorReason.WORK_SIGNIN)))
            close(); return@callbackFlow
        }
        val listener = firestore.collection("publicaciones").orderBy("CreadoEn", Query.Direction.DESCENDING)
            .limit(50).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(AppResult.Error(DomainError.Known(ErrorReason.WORK_LOAD)))
                } else if (soloServidor && snapshot?.metadata?.isFromCache == true) {
                    trySend(AppResult.Error(DomainError.Network))
                } else if (soloServidor && (snapshot == null || snapshot.metadata.hasPendingWrites())) {
                    // No reemplazar la copia completa con escrituras aún no confirmadas.
                } else try {
                    trySend(AppResult.Success(snapshot?.documents.orEmpty().map { doc ->
                        Publicacion(doc.id, doc.getString("BarberoId").orEmpty(),
                            doc.getString("BarberoNombre").orEmpty(), doc.getString("ImagenUrl").orEmpty(),
                            doc.getString("Descripcion").orEmpty(), doc.getTimestamp("CreadoEn")?.toDate()?.time,
                            esPropia = doc.getString("BarberoId") == uid)
                    }))
                } catch (e: Exception) {
                    trySend(AppResult.Error(DomainError.Known(ErrorReason.WORK_INVALID)))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = auth.currentUser?.uid ?: error("Iniciá sesión nuevamente.")
            val perfil = firestore.collection("barberos").document(uid)
            val barbero = perfil.get(Source.SERVER).esperar()
            check(barbero.getBoolean("Estado") == true) { "Solo un barbero habilitado puede publicar." }
            check(cloudName.matches(Regex("[A-Za-z0-9_-]+")) && uploadPreset.isNotBlank()) {
                "Falta conectar Cloudinary. Configurá el cloud name y el upload preset y volvé a compilar."
            }
            val key = "$uid/${publicacion.solicitudId}"
            val reference = firestore.collection("publicaciones").document(publicacion.solicitudId)
            val existente = reference.get(Source.SERVER).esperar()
            if (existente.exists()) {
                check(existente.getString("BarberoId") == uid) { "La publicación pertenece a otra cuenta." }
                pendientes.remove(key)
                return@withContext AppResult.Success(Unit)
            }
            val imagen = pendientes[key] ?: run {
                val bytes = preparador.preparar(publicacion.imagenLocal, publicacion.recorteX, publicacion.recorteY)
                api.subir(cloudName, uploadPreset.toRequestBody("text/plain".toMediaType()),
                    MultipartBody.Part.createFormData("file", "trabajo.jpg", bytes.toRequestBody("image/jpeg".toMediaType())))
                    .also {
                        check(it.secureUrl.startsWith("https://res.cloudinary.com/$cloudName/image/upload/")) {
                            "El servicio devolvió una dirección de imagen inválida."
                        }
                        pendientes[key] = it
                    }
            }
            check(auth.currentUser?.uid == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
            firestore.runTransaction { tx ->
                val actual = tx.get(reference)
                val propietario = tx.get(perfil)
                check(propietario.getBoolean("Estado") == true) { "El barbero ya no está habilitado." }
                if (!actual.exists()) tx.set(reference, mapOf(
                    "BarberoId" to uid, "BarberoNombre" to propietario.getString("Nombre").orEmpty(),
                    "ImagenUrl" to imagen.secureUrl, "ImagenPublicId" to imagen.publicId,
                    "Descripcion" to publicacion.descripcion, "CreadoEn" to FieldValue.serverTimestamp()))
                else check(actual.getString("BarberoId") == uid)
            }.esperar()
            pendientes.remove(key)
            AppResult.Success(Unit)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            AppResult.Error(DomainError.Unknown(when (e) {
                is FirebaseFirestoreException -> "No se pudo guardar la publicación. Revisá la conexión y las reglas de Firestore; podés reintentar."
                is retrofit2.HttpException -> "Cloudinary rechazó la foto. Revisá el cloud name y el preset unsigned con formato JPG."
                else -> e.message ?: "No se pudo publicar el trabajo."
            }))
        }
    }

    override suspend fun modificarDescripcion(id: String, descripcion: String): AppResult<Unit> = try {
        require(descripcion.length <= 300)
        val uid = auth.currentUser?.uid ?: error("Iniciá sesión nuevamente.")
        firestore.runTransaction { tx ->
            val ref = firestore.collection("publicaciones").document(id)
            val doc = tx.get(ref)
            val perfil = tx.get(firestore.collection("barberos").document(uid))
            check(perfil.getBoolean("Estado") == true && doc.getString("BarberoId") == uid) {
                "Solo podés modificar tus propias publicaciones."
            }
            tx.update(ref, "Descripcion", descripcion)
        }.esperar()
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        AppResult.Error(DomainError.Known(ErrorReason.DESCRIPTION_SAVE))
    }

    override suspend fun eliminar(id: String): AppResult<Unit> = try {
        val uid = auth.currentUser?.uid ?: error("Iniciá sesión nuevamente.")
        firestore.runTransaction { tx ->
            val ref = firestore.collection("publicaciones").document(id)
            val doc = tx.get(ref)
            val perfil = tx.get(firestore.collection("barberos").document(uid))
            check(perfil.getBoolean("Estado") == true && doc.getString("BarberoId") == uid) {
                "Solo podés eliminar tus propias publicaciones."
            }
            tx.delete(ref)
        }.esperar()
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Error(DomainError.Known(ErrorReason.WORK_DELETE)) }
}

private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación fallida"))
        }
    }
}
