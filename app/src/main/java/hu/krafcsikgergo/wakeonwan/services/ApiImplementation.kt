package hu.krafcsikgergo.wakeonwan.services

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiImplementation {

    var baseUrl = "http://176.63.185.112:9753/"

    fun getInstance() : ServerApi{
        return Retrofit.Builder().baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            // we need to add converter factory to
            // convert JSON object to Java object
            .build()
            .create(ServerApi::class.java)
    }
}