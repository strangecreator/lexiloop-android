package ru.lexiloop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.lexiloop.app.data.repo.parseDrfError

class DrfErrorParsingTest {

    @Test
    fun `detail messages pass through`() {
        assertEquals(
            "Invalid username or password.",
            parseDrfError("""{"detail": "Invalid username or password."}"""),
        )
    }

    @Test
    fun `field errors include the field name`() {
        assertEquals(
            "password: This field may not be blank.",
            parseDrfError("""{"password": ["This field may not be blank."]}"""),
        )
    }

    @Test
    fun `non-field errors drop the prefix`() {
        assertEquals(
            "Something went wrong.",
            parseDrfError("""{"non_field_errors": ["Something went wrong."]}"""),
        )
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(parseDrfError("<html>502 Bad Gateway</html>"))
        assertNull(parseDrfError(""))
        assertNull(parseDrfError("[1, 2, 3]"))
    }
}
