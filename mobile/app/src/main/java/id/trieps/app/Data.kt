package id.trieps.app

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }

class TokenVault(context: Context) {
 private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
 private fun key(): SecretKey {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey("trieps_token", null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("trieps_token",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 var token: String
  get() = runCatching {
   val data = Base64.decode(prefs.getString("token", ""), Base64.NO_WRAP)
   if(data.size < 13) "" else Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))); String(doFinal(data.copyOfRange(12,data.size))) }
  }.getOrDefault("")
  set(value) { if(value.isEmpty()) prefs.edit().clear().apply() else { val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key()); prefs.edit().putString("token",Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply() } }
}

class Api(val context: Context) {
 val vault = TokenVault(context)
 private val client = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
 suspend fun request(path:String, method:String="GET", body:JSONObject?=null):String = withContext(Dispatchers.IO) {
  val req=Request.Builder().url(BuildConfig.API_URL.trimEnd('/')+"/"+path).header("Accept","application/json")
  vault.token.takeIf { it.isNotEmpty() }?.let { req.header("Authorization","Bearer $it") }
  req.method(method, if(method=="GET") null else (body?.toString()?:"{}").toRequestBody("application/json".toMediaType()))
  client.newCall(req.build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) { val o=runCatching{JSONObject(text)}.getOrNull(); val errors=o?.optJSONObject("errors"); val detail=errors?.keys()?.asSequence()?.firstOrNull()?.let{errors.optJSONArray(it)?.optString(0)}; throw Exception(detail?:o?.optString("message")?.takeIf{it.isNotBlank()}?:"Server tidak tersedia (${r.code})") }; text }
 }
 suspend fun obj(path:String, method:String="GET", body:JSONObject?=null)=JSONObject(request(path,method,body).ifBlank{"{}"})
 suspend fun list(path:String)=JSONArray(request(path)).objects()
 suspend fun upload(uri:android.net.Uri):String=withContext(Dispatchers.IO){
  val type=context.contentResolver.getType(uri)?:"image/jpeg"
  val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:error("Foto tidak dapat dibaca")
  require(bytes.size<=8*1024*1024){"Foto maksimal 8 MB"}
  val ext=if(type=="image/png")"png" else if(type=="image/webp")"webp" else "jpg"
  val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("photo","touring.$ext",bytes.toRequestBody(type.toMediaType())).build()
  client.newCall(Request.Builder().url(BuildConfig.API_URL+"photos").header("Accept","application/json").header("Authorization","Bearer ${vault.token}").post(body).build()).execute().use{r->val s=r.body?.string().orEmpty();check(r.isSuccessful){"Unggah foto gagal"};JSONObject(s).getString("path")}
 }
}

class TrackStore(context:Context):SQLiteOpenHelper(context,"trieps-offline.db",null,1){
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE points (client_id TEXT PRIMARY KEY, tour_id INTEGER NOT NULL, user_id INTEGER NOT NULL, latitude REAL, longitude REAL, accuracy REAL, recorded_at TEXT, synced INTEGER DEFAULT 0)");db.execSQL("CREATE INDEX pending ON points(user_id,synced,tour_id)")}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 fun add(tour:Long,user:Long,lat:Double,lon:Double,accuracy:Float,capturedAt:String=java.time.Instant.now().toString()){writableDatabase.execSQL("INSERT INTO points(client_id,tour_id,user_id,latitude,longitude,accuracy,recorded_at) VALUES(?,?,?,?,?,?,?)",arrayOf<Any>(java.util.UUID.randomUUID().toString(),tour,user,lat,lon,accuracy,capturedAt))}
 fun points(tour:Long,user:Long,pending:Boolean=false,limit:Int=10000):List<JSONObject>{val out=mutableListOf<JSONObject>();readableDatabase.rawQuery("SELECT client_id,latitude,longitude,accuracy,recorded_at FROM points WHERE tour_id=? AND user_id=? ${if(pending)"AND synced=0" else ""} ORDER BY recorded_at LIMIT $limit",arrayOf(tour.toString(),user.toString())).use{c->while(c.moveToNext())out+=json("client_id" to c.getString(0),"latitude" to c.getDouble(1),"longitude" to c.getDouble(2),"accuracy" to c.getDouble(3),"recorded_at" to c.getString(4))};return out}
 fun pendingTours(user:Long):List<Long>{val out=mutableListOf<Long>();readableDatabase.rawQuery("SELECT DISTINCT tour_id FROM points WHERE synced=0 AND user_id=?",arrayOf(user.toString())).use{while(it.moveToNext())out+=it.getLong(0)};return out}
 fun ack(ids:JSONArray){writableDatabase.beginTransaction();try{for(i in 0 until ids.length())writableDatabase.execSQL("UPDATE points SET synced=1 WHERE client_id=?",arrayOf(ids.getString(i)));writableDatabase.setTransactionSuccessful()}finally{writableDatabase.endTransaction()}}
}

class SyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {val api=Api(applicationContext);if(api.vault.token.isBlank())return Result.success();return try{val user=api.obj("me").getLong("id");TrackStore(applicationContext).use{db->for(t in db.pendingTours(user)){var batch=db.points(t,user,true,500);while(batch.isNotEmpty()){val response=api.obj("tours/$t/points","POST",json("points" to JSONArray(batch)));db.ack(response.getJSONArray("accepted"));batch=db.points(t,user,true,500)}}};Result.success()}catch(_:Exception){Result.retry()}}
 companion object {fun enqueue(context:Context){WorkManager.getInstance(context).enqueueUniqueWork("trieps-sync",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())}}
}
class TriepsApplication:Application(){override fun onCreate(){super.onCreate();org.osmdroid.config.Configuration.getInstance().userAgentValue=packageName;val work=WorkManager.getInstance(this);work.enqueueUniquePeriodicWork("trieps-periodic-sync",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());work.enqueueUniquePeriodicWork("trieps-maintenance",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ReminderWorker>(12,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())}}
