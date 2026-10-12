package com.lovelydays.data

import com.lovelydays.data.auth.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun hashIsNotThePasswordAndVerifies() {
        val salt = PasswordHasher.newSalt()
        val encoded = PasswordHasher.encode(PasswordHasher.hash("secret-pass", salt))
        assertNotEquals("secret-pass", encoded)
        assertTrue(PasswordHasher.verify("secret-pass", salt, PasswordHasher.decode(encoded)))
        assertFalse(PasswordHasher.verify("other-pass", salt, PasswordHasher.decode(encoded)))
        val otherSalt = PasswordHasher.newSalt()
        assertFalse(
            PasswordHasher.encode(PasswordHasher.hash("secret-pass", salt)) ==
                PasswordHasher.encode(PasswordHasher.hash("secret-pass", otherSalt)),
        )
    }
}
