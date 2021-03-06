
package com.example.teamprojmobv.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.teamprojmobv.data.db.LocalCache
import com.example.teamprojmobv.data.db.model.MediaItem
import com.example.teamprojmobv.data.util.ChCrypto
import com.example.viewmodel.data.db.model.UserItem
import com.opinyour.android.app.data.api.WebApi
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.ConnectException



/**
 * Repository class that works with local and remote data sources.
 */
class DataRepository private constructor(
    private val api: WebApi,
    private val cache: LocalCache
) {

    private lateinit var token: String
    private lateinit var pwd: String
    private lateinit var loggedUser : UserItem
    private  var imageUri : Uri? = null


    companion object {
        const val TAG = "DataRepository"
        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(api: WebApi, cache: LocalCache): DataRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: DataRepository(api, cache).also { INSTANCE = it }
            }
    }

    fun getActualUsers(): LiveData<List<UserItem>> = cache.getActualUsers()
    suspend fun getActualUser(): LiveData<UserItem> = cache.getActualUser()
    suspend fun deleteUsers() = cache.deleteUsers()
    fun getPassword():String = pwd

    //tu by som asi mala tahat prihlaseneho uzivatela z cache vsak na co to mame
    fun getLoggedUser():UserItem{
        return loggedUser
    }

    fun getImageUri(): Uri? {
        return imageUri
    }

    fun setImageUri(uri:Uri){
        imageUri=uri
    }

    fun resetUserInfo(){
        imageUri = null
        token = ""
        pwd = ""
        loggedUser = UserItem(-1, "", "", "", "", "", -1)
    }

    suspend fun createUser(
        action: String,
        apikey: String,
        email: String,
        username: String,
        password: String
    ):Boolean {

        try {
            val jsonObject = JSONObject()
            jsonObject.put("action", action)
            jsonObject.put("apikey", apikey)
            jsonObject.put("email", email)
            jsonObject.put("username", username)
            // password zadava pouzivatel, SYM_ENC_KEY je 32 char string
            val passwordEnc = ChCrypto.aesEncrypt(password, ApiConstants.SYM_ENC_KEY)
            jsonObject.put("password", passwordEnc)
            val jsonObjectString = jsonObject.toString()
            // Create RequestBody ( We're not using any converter, like GsonConverter, MoshiConverter e.t.c, that's why we use RequestBody )
            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

            val response = api.createUser(requestBody)
            if (response.isSuccessful) {
                response.body()?.let {
                    val currentTimestamp = System.currentTimeMillis()
                    loggedUser = UserItem(it.id, it.username, it.email, it.token, it.refresh, it.profile, currentTimestamp)
                    token = it.token
                    pwd = password
                    Log.i("DataRepository", token + " " + pwd)
                    cache.insertUser(
                        UserItem(
                            it.id,
                            it.username,
                            it.email,
                            it.token,
                            it.refresh,
                            it.profile,
                            currentTimestamp
                        )
                    )
                    return true
                    }
                }
        } catch (ex: ConnectException) {

            ex.printStackTrace()
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }
        return false
    }

    suspend fun loginUser(
        action: String,
        apikey: String,
        username: String,
        password: String
    ): Boolean {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("action", action)
            jsonObject.put("apikey", apikey)
            jsonObject.put("username", username)
            val passwordEnc = ChCrypto.aesEncrypt(password, ApiConstants.SYM_ENC_KEY)
            jsonObject.put("password", passwordEnc)
            val jsonObjectString = jsonObject.toString()
            // Create RequestBody ( We're not using any converter, like GsonConverter, MoshiConverter e.t.c, that's why we use RequestBody )
            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

            val response = api.loginUser(requestBody)
            if (response.isSuccessful) {
                response.body()?.let {
                     //cache.deleteUsers()
                    val currentTimestamp = System.currentTimeMillis()
                    token = it.token
                    pwd = password
                    loggedUser = UserItem(it.id, it.username, it.email, it.token, it.refresh, it.profile, currentTimestamp)
                    cache.insertUser(
                         UserItem(it.id, it.username, it.email, it.token, it.refresh, it.profile, currentTimestamp)
                     )
                    Log.i("DataRepository", token + " " + pwd)
                    return true
                }
            }
        } catch (ex: ConnectException) {
            ex.printStackTrace()
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }
        return false
    }

    suspend fun getVideos(action: String,
                          apikey: String) : ArrayList<MediaItem>? {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("action", action)
            jsonObject.put("apikey", apikey)
            jsonObject.put("token", token)
            val jsonObjectString = jsonObject.toString()
            // Create RequestBody ( We're not using any converter, like GsonConverter, MoshiConverter e.t.c, that's why we use RequestBody )
            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

            val response = api.getVideos(requestBody)
            if (response.isSuccessful) {
                response.body()?.let {

                    for(i in it){
                        i.videourl = "http://api.mcomputing.eu/mobv/uploads/" + i.videourl
                    }
//                    (int i  = 0; i < it.size(); i++){
//                        it.get(i).videourl = "http://api.mcomputing.eu/mobv/uploads/" + it.get(i).videourl;
//                    }
                    
//                    val mediaData : MutableLiveData<MutableList<MediaItem>> = MutableLiveData(it)
//                    val pom2 = mediaData.value?.get(1)?.videourl
                    return it
                }
            }
        } catch (ex: ConnectException) {
            ex.printStackTrace()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }


    suspend fun uploadVideo(
        filePath: String,
        apikey: String,
    ) {
        try {
            val file = File(filePath)
            val jsonData = JSONObject()
            jsonData.put("apikey", apikey)
            jsonData.put("token", token)
            val jsonDataString = jsonData.toString()
            val json= jsonDataString.toRequestBody("application/json".toMediaTypeOrNull())
            val video = file.asRequestBody("video/mp4".toMediaTypeOrNull())
            val filePart =
                MultipartBody.Part.createFormData("video", "video" ,video)

            val responseVideo = api.createVideo(filePart, json)

            Log.i("DataRepository upload", responseVideo.toString())
            Log.i("DataRepository upload", responseVideo.body().toString())
            if (responseVideo.isSuccessful) {
                responseVideo.body()?.let {
                    Log.i("DataRepository upload", it.string())
                    return
                }
            }
        } catch (ex: ConnectException) {
            ex.printStackTrace()
            return
        } catch (ex: Exception) {
            Log.e("DataRepository upload", ex.toString())
            ex.printStackTrace()
            return
        }
    }



    suspend fun changePassword(
        action: String,
        apikey: String,
        newPwd: String
    ) : Boolean{
        try {
            val jsonObject = JSONObject()
            jsonObject.put("action", action)
            jsonObject.put("apikey", apikey)
            jsonObject.put("token", token)
            jsonObject.put("oldpassword", ChCrypto.aesEncrypt(pwd, ApiConstants.SYM_ENC_KEY))
            jsonObject.put("newpassword", ChCrypto.aesEncrypt(newPwd, ApiConstants.SYM_ENC_KEY))
            val jsonObjectString = jsonObject.toString()
            val requestBody=
                jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
            val response = api.changePassword(requestBody)

            Log.i("changepassword", response.toString())  //{protocol=http/1.1, code=200, message=OK... vratilo request

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.i("changepassword", "old: " + pwd + " new: " + newPwd)
                    pwd = newPwd
                    token = it.token
                    val currentTimestamp = System.currentTimeMillis()
                    // TODO bohvie ci toto funguje
                    // cache.deleteUsers()   //nejde lebo java.lang.IllegalStateException: Cannot access
                    // database on the main thread since it may potentially lock the UI for a long period of time.
                    loggedUser = UserItem(it.id, it.username, it.email, it.token, it.refresh, it.profile, currentTimestamp)
                    cache.insertUser(
                        UserItem(it.id, it.username, it.email, it.token, it.refresh, it.profile, currentTimestamp)
                    )
                    return true
                }
            }
            return false
        } catch (ex: ConnectException) {
            ex.printStackTrace()
            return false
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }
    }

    suspend fun uploadProfilePhoto(
        filePath: String,
        apikey: String,
    ):Boolean {
        try {
            val file = File(filePath)
            val jsonData = JSONObject()
            jsonData.put("apikey", apikey)
            jsonData.put("token", token)
            val jsonDataString = jsonData.toString()
            val json= jsonDataString.toRequestBody("application/json".toMediaTypeOrNull())
            val image = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("image", "image" ,image)

            val response = api.createPhoto(filePart, json)

            Log.i("DataRepository upload", response.toString())           //vracia 400
            Log.i("DataRepository upload", response.body().toString())   //vracia null
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.i("DataRepository upload", it.string())   //json vracia {"status":"success"}
                    return true
                }
            }
            return false
        } catch (ex: ConnectException) {
            ex.printStackTrace()
            return false
        } catch (ex: Exception) {
            Log.e("DataRepository upload", ex.toString())
            ex.printStackTrace()
            return false
        }
    }
}

