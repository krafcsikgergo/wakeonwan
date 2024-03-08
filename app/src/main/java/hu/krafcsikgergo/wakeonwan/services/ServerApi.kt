package hu.krafcsikgergo.wakeonwan.services

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

interface ServerApi {
    @GET("/wakeup")
    suspend fun wakeUp(): Response<ResponseBody>

    @GET("/test")
    suspend fun getStatus(): Response<ResponseBody>

    @GET("/")
    suspend fun checkHealth(): Response<ResponseBody>

    @GET("/shutdown")
    suspend fun shutDown(): Response<ResponseBody>

    @GET("/schedules")
    suspend fun getSchedules(): Response<ResponseBody>

    @POST("/schedules")
    suspend fun saveSchedule(schedule: Schedule): Response<ResponseBody>

    @DELETE("/schedules/{index}")
    suspend fun deleteSchedule(index: Int): Response<ResponseBody>
}