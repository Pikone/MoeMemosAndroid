package me.mudkip.moememos.viewmodel

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.isSuccess
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.withContext
import me.mudkip.moememos.R
import me.mudkip.moememos.data.api.MemosApiService
import me.mudkip.moememos.data.api.SignInInput
import me.mudkip.moememos.data.constant.MoeMemosException
import me.mudkip.moememos.data.model.Status
import me.mudkip.moememos.data.model.User
import me.mudkip.moememos.data.repository.UserRepository
import me.mudkip.moememos.ext.string
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class UserStateViewModel @Inject constructor(
    private val memosApiService: MemosApiService,
    private val userRepository: UserRepository
) : ViewModel() {

    var currentUser: User? by mutableStateOf(null)

    val host: String get() = memosApiService.host ?: ""
    val status: Status? get() = memosApiService.status
    val okHttpClient: OkHttpClient get() = memosApiService.client

    suspend fun loadCurrentUser(): ApiResponse<User> = withContext(viewModelScope.coroutineContext) {
        userRepository.getCurrentUser().suspendOnSuccess {
            currentUser = data
        }
    }

    suspend fun login(host: String, email: String, password: String): ApiResponse<User> = withContext(viewModelScope.coroutineContext) {
        try {
            val (_, client) = memosApiService.createClient(host, null)
            client.signIn(SignInInput(email, email, password)).getOrThrow()
            val resp = client.me()
            if (resp.isSuccess) {
                memosApiService.update(host, null)
                currentUser = resp.getOrNull()
            }
            resp
        } catch (e: Throwable) {
            ApiResponse.error(e)
        }
    }

    suspend fun login(memosOpenApi: String): ApiResponse<User> = withContext(viewModelScope.coroutineContext) {
        try {
            val uri = Uri.parse(memosOpenApi)
            val openId = uri.getQueryParameter("openId")
            if (openId == null || openId.isEmpty()) {
                throw MoeMemosException.invalidOpenAPI
            }

            val host = uri.buildUpon().path("/").clearQuery().fragment("").build().toString()
            val resp = memosApiService.createClient(host, openId).second.me()
            if (resp.isSuccess) {
                memosApiService.update(host, openId)
                currentUser = resp.getOrNull()
            }
            resp
        } catch (e: Throwable) {
            ApiResponse.error(e)
        }
    }

    suspend fun logout() = withContext(viewModelScope.coroutineContext) {
        memosApiService.call {
            it.logout()
        }.suspendOnSuccess {
            memosApiService.update(host, null)
            currentUser = null
        }
    }
}

val LocalUserState =
    compositionLocalOf<UserStateViewModel> { error(R.string.user_state_not_found.string) }