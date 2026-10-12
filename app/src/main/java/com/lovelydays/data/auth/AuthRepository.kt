package com.lovelydays.data.auth

import android.database.sqlite.SQLiteConstraintException
import com.lovelydays.data.db.UserDao
import com.lovelydays.data.db.UserEntity
import com.lovelydays.data.session.SessionStore
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AuthFailure {
    EMPTY,
    LOGIN_LENGTH,
    LOGIN_INVALID,
    PASSWORD_SHORT,
    PASSWORD_LONG,
    PASSWORD_MISMATCH,
    USER_EXISTS,
    UNKNOWN_USER,
    WRONG_PASSWORD,
}

sealed interface AuthOutcome {
    data class Success(val userId: Long) : AuthOutcome
    data class Failed(val reason: AuthFailure) : AuthOutcome
}

class AuthRepository(
    private val users: UserDao,
    private val session: SessionStore,
) {
    suspend fun register(loginRaw: String, password: String, confirm: String): AuthOutcome =
        withContext(Dispatchers.Default) {
            validate(loginRaw, password, confirm)?.let { return@withContext AuthOutcome.Failed(it) }
            val login = normalize(loginRaw)
            if (users.findByLogin(login) != null) {
                return@withContext AuthOutcome.Failed(AuthFailure.USER_EXISTS)
            }
            val salt = PasswordHasher.newSalt()
            val hash = PasswordHasher.hash(password, salt)
            val id = try {
                users.insert(
                    UserEntity(
                        login = login,
                        passwordHash = PasswordHasher.encode(hash),
                        salt = PasswordHasher.encode(salt),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } catch (_: SQLiteConstraintException) {
                return@withContext AuthOutcome.Failed(AuthFailure.USER_EXISTS)
            }
            session.setUserId(id)
            AuthOutcome.Success(id)
        }

    suspend fun signIn(loginRaw: String, password: String): AuthOutcome =
        withContext(Dispatchers.Default) {
            validate(loginRaw, password, confirm = null)?.let { return@withContext AuthOutcome.Failed(it) }
            val login = normalize(loginRaw)
            val user = users.findByLogin(login)
                ?: return@withContext AuthOutcome.Failed(AuthFailure.UNKNOWN_USER)
            val salt = PasswordHasher.decode(user.salt)
            val expected = PasswordHasher.decode(user.passwordHash)
            if (!PasswordHasher.verify(password, salt, expected)) {
                return@withContext AuthOutcome.Failed(AuthFailure.WRONG_PASSWORD)
            }
            session.setUserId(user.id)
            AuthOutcome.Success(user.id)
        }

    companion object {
        private val LOGIN = Regex("^[\\p{L}\\p{N}._]{3,32}$")

        fun normalize(loginRaw: String): String = loginRaw.trim().lowercase(Locale.ROOT)

        fun validate(loginRaw: String, password: String, confirm: String?): AuthFailure? {
            val login = loginRaw.trim()
            if (login.isEmpty() || password.isEmpty()) return AuthFailure.EMPTY
            if (login.length !in 3..32) return AuthFailure.LOGIN_LENGTH
            if (!LOGIN.matches(normalize(loginRaw))) return AuthFailure.LOGIN_INVALID
            if (password.length < 6) return AuthFailure.PASSWORD_SHORT
            if (password.length > 128) return AuthFailure.PASSWORD_LONG
            if (confirm != null && confirm != password) return AuthFailure.PASSWORD_MISMATCH
            return null
        }

        fun message(failure: AuthFailure): String = when (failure) {
            AuthFailure.EMPTY -> "Введите логин и пароль"
            AuthFailure.LOGIN_LENGTH -> "Логин — от 3 до 32 символов"
            AuthFailure.LOGIN_INVALID -> "Логин: буквы, цифры, точка или подчёркивание"
            AuthFailure.PASSWORD_SHORT -> "Пароль — не короче 6 символов"
            AuthFailure.PASSWORD_LONG -> "Пароль слишком длинный"
            AuthFailure.PASSWORD_MISMATCH -> "Пароли не совпадают"
            AuthFailure.USER_EXISTS -> "Такой логин уже есть"
            AuthFailure.UNKNOWN_USER -> "Не нашли такой логин"
            AuthFailure.WRONG_PASSWORD -> "Неверный пароль"
        }
    }
}
