package com.xmov.metahuman.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.also

object JsonUtils {

    /**
     * 读取 assets 目录下的 JSON 数组文件
     * @param context 上下文
     * @param fileName assets 目录下的 JSON 文件名（如 "data_array.json"）
     * @return 解析后的 JSONArray，失败返回 null
     */
    fun readAssetsJsonArray(context: Context, fileName: String): JSONArray? {
        return try {
            // 获取 assets 管理器，打开文件输入流
            val inputStream = context.assets.open(fileName)
            // 读取流内容为字符串
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val stringBuilder = kotlin.text.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            reader.close()
            inputStream.close()
            //  解析字符串为 JSONArray
            JSONArray(stringBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
            null // 文件不存在或读取失败
        } catch (e: JSONException) {
            e.printStackTrace()
            null // JSON 格式错误（非数组）
        }
    }
}