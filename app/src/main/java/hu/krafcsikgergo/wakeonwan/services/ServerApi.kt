package hu.krafcsikgergo.wakeonwan.services

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET

interface ServerApi {
    @GET("/wakeup")
    suspend fun wakeUp(): Response<ResponseBody>

    @GET("/test")
    suspend fun getStatus(): Response<ResponseBody>

    @GET("/")
    suspend fun checkHealth(): Response<ResponseBody>

    @GET("/shutdown")
    suspend fun shutDown(): Response<ResponseBody>
}