package ar.edu.unaj.am.myapplication.core.domain.model

/** Carries a reason across validation boundaries without storing translated text. */
class DomainValidationException(val error: DomainError.Known) : IllegalArgumentException(error.reason.name)

fun requireDomain(condition: Boolean, reason: ErrorReason, dayOfWeek: Int? = null) {
    if (!condition) throw DomainValidationException(DomainError.Known(reason, dayOfWeek))
}
