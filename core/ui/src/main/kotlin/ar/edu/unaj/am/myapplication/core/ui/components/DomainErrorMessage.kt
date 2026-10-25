package ar.edu.unaj.am.myapplication.core.ui.components

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.ui.R

/**
 * El dominio dice QUÉ pasó ([DomainError]); la UI decide CÓMO contarlo.
 * Usar string resources permite traducir la app sin tocar ViewModels ni dominio.
 */
@Composable
fun DomainError.asMessage(): String = when (this) {
    DomainError.Network -> stringResource(R.string.core_ui_error_network)
    DomainError.NotFound -> stringResource(R.string.core_ui_error_not_found)
    DomainError.EmptyTitle -> stringResource(R.string.core_ui_error_empty_title)
    is DomainError.Known -> {
        val text = stringResource(reason.resourceId())
        val day = dayOfWeek
        if (day == null) text else stringResource(
            R.string.error_on_day,
            java.time.DayOfWeek.of(day).getDisplayName(
                java.time.format.TextStyle.FULL,
                androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            ),
            text
        )
    }
    is DomainError.Unknown -> message ?: stringResource(R.string.core_ui_error_unknown)
}

/** Exhaustive mapping keeps new domain errors from missing their translations. */
internal fun ErrorReason.resourceId(): Int = when (this) {
    ErrorReason.GOOGLE_SIGNIN -> R.string.error_google_signin
    ErrorReason.EMAIL_EXISTS -> R.string.error_email_exists
    ErrorReason.WEAK_PASSWORD -> R.string.error_weak_password
    ErrorReason.INVALID_EMAIL -> R.string.error_invalid_email
    ErrorReason.REGISTER_FAILED -> R.string.error_register_failed
    ErrorReason.PROFILE_SIGNIN -> R.string.error_profile_signin
    ErrorReason.MISSING_EMAIL -> R.string.error_missing_email
    ErrorReason.PROFILE_PERMISSION -> R.string.error_profile_permission
    ErrorReason.PROFILE_LOAD -> R.string.error_profile_load
    ErrorReason.WORK_SIGNIN -> R.string.error_work_signin
    ErrorReason.WORK_LOAD -> R.string.error_work_load
    ErrorReason.WORK_INVALID -> R.string.error_work_invalid
    ErrorReason.DESCRIPTION_SAVE -> R.string.error_description_save
    ErrorReason.WORK_DELETE -> R.string.error_work_delete
    ErrorReason.APPOINTMENTS_SIGNIN -> R.string.error_appointments_signin
    ErrorReason.APPOINTMENTS_CACHE -> R.string.error_appointments_cache
    ErrorReason.NAMES_SYNC -> R.string.error_names_sync
    ErrorReason.APPOINTMENTS_SYNC -> R.string.error_appointments_sync
    ErrorReason.APPOINTMENT_INVALID_FIELDS -> R.string.error_appointment_invalid_fields
    ErrorReason.LOCATION_PERMISSION -> R.string.error_location_permission
    ErrorReason.LOCATION_LOAD -> R.string.error_location_load
    ErrorReason.ROUTE_INVALID -> R.string.error_route_invalid
    ErrorReason.ROUTE_MISSING -> R.string.error_route_missing
    ErrorReason.WORK_CACHE -> R.string.error_work_cache
    ErrorReason.PHOTOS_CACHE -> R.string.error_photos_cache
    ErrorReason.WORK_SYNC -> R.string.error_work_sync
    ErrorReason.UNAVAILABLE -> R.string.error_unavailable
    ErrorReason.SCHEDULE_ZONE -> R.string.error_schedule_zone
    ErrorReason.WEEK_SELECTION -> R.string.error_week_selection
    ErrorReason.LOGIN_REQUIRED_FIELDS -> R.string.error_login_required_fields
    ErrorReason.GOOGLE_ACCOUNT -> R.string.error_google_account
    ErrorReason.APPOINTMENT_PENDING -> R.string.error_appointment_pending
    ErrorReason.APPOINTMENT_NOT_STARTED -> R.string.error_appointment_not_started
    ErrorReason.NAME_LENGTH -> R.string.error_name_length
    ErrorReason.PHOTO_REQUIRED -> R.string.error_photo_required
    ErrorReason.DESCRIPTION_LENGTH -> R.string.error_description_length
    ErrorReason.PHOTO_CROP -> R.string.error_photo_crop
    ErrorReason.POST_INVALID -> R.string.error_post_invalid
    ErrorReason.BOOKING_SELECTION -> R.string.error_booking_selection
    ErrorReason.BOOKING_FIELDS -> R.string.error_booking_fields
    ErrorReason.LOCATION_INVALID -> R.string.error_location_invalid
    ErrorReason.ROUTE_COORDINATES -> R.string.error_route_coordinates
    ErrorReason.GOOGLE_OPEN -> R.string.error_google_open
    ErrorReason.GOOGLE_UNAVAILABLE -> R.string.error_google_unavailable
    ErrorReason.BOOKING_UPDATE -> R.string.error_booking_update
    ErrorReason.PASSWORD_LENGTH -> R.string.error_password_length
    ErrorReason.PASSWORD_CONFIRMATION -> R.string.error_password_confirmation
    ErrorReason.LOGIN_FAILED -> R.string.error_login_failed
    ErrorReason.SCHEDULE_DAYS -> R.string.error_schedule_days
    ErrorReason.SCHEDULE_EMPTY_DAY -> R.string.error_schedule_empty_day
    ErrorReason.SCHEDULE_TIME_FORMAT -> R.string.error_schedule_time_format
    ErrorReason.SCHEDULE_DURATION -> R.string.error_schedule_duration
    ErrorReason.SCHEDULE_OVERLAP -> R.string.error_schedule_overlap
    ErrorReason.SCHEDULE_WEEK_CHANGED -> R.string.error_schedule_week_changed
    ErrorReason.SCHEDULE_END -> R.string.error_schedule_end
    ErrorReason.APPOINTMENT_START_MISSING -> R.string.error_appointment_start_missing
    ErrorReason.APPOINTMENT_END_MISSING -> R.string.error_appointment_end_missing
    ErrorReason.APPOINTMENT_DURATION -> R.string.error_appointment_duration
}
