package id.trieps.app
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.work.*
import com.google.android.gms.location.*
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*

fun channel(c:Context,id:String,name:String){(c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(id,name,NotificationManager.IMPORTANCE_LOW))}
fun ongoing(c:Context,title:String,text:String,id:String):Notification = NotificationCompat.Builder(c,id).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText(text).setContentIntent(PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).setOngoing(true).build()
object RideState {var tourId by mutableLongStateOf(0L);var count by mutableIntStateOf(0);var gpsMessage by mutableStateOf("Menunggu GPS…")}
class TrackingService:Service(){
 private lateinit var fused:FusedLocationProviderClient;private lateinit var store:TrackStore
 private var tour=0L;private var user=0L
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){for(l in result.locations){if(l.accuracy>100)continue;store.add(tour,user,l.latitude,l.longitude,l.accuracy,java.time.Instant.ofEpochMilli(l.time).toString());RideState.count++;RideState.gpsMessage="GPS ±${l.accuracy.toInt()} m · tersimpan di HP"};SyncWorker.enqueue(this@TrackingService)}}
 override fun onCreate(){super.onCreate();channel(this,"tracking","Perekaman touring");store=TrackStore(this);fused=LocationServices.getFusedLocationProviderClient(this)}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
 val p=getSharedPreferences("tracking",MODE_PRIVATE);tour=intent?.getLongExtra("tour",0)?.takeIf{it>0}?:p.getLong("tour",0);user=intent?.getLongExtra("user",0)?.takeIf{it>0}?:p.getLong("user",0)
 if(tour==0L||user==0L||ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY}
 p.edit().putLong("tour",tour).putLong("user",user).apply();RideState.tourId=tour
 startForeground(101,ongoing(this,"Trieps sedang merekam","Rute disimpan tanpa internet. Ketuk untuk membuka.","tracking"))
 try{fused.removeLocationUpdates(callback);fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5000).setMinUpdateDistanceMeters(5f).setMaxUpdateDelayMillis(10000).build(),callback,Looper.getMainLooper())}catch(_:SecurityException){stopSelf()}
 scope.launch{while(isActive){SyncWorker.enqueue(this@TrackingService);delay(20000)}};return START_STICKY}
 override fun onDestroy(){fused.removeLocationUpdates(callback);scope.cancel();store.close();RideState.tourId=0;super.onDestroy()}
 override fun onBind(intent:Intent?)=null
 companion object{fun start(c:Context,tour:Long,user:Long){ContextCompat.startForegroundService(c,Intent(c,TrackingService::class.java).putExtra("tour",tour).putExtra("user",user))};fun stop(c:Context){c.getSharedPreferences("tracking",Context.MODE_PRIVATE).edit().clear().apply();c.stopService(Intent(c,TrackingService::class.java));RideState.tourId=0;SyncWorker.enqueue(c)}}
}
object VoiceState{var connected by mutableStateOf(false);var muted by mutableStateOf(false);var status by mutableStateOf("");var participants by mutableStateOf(listOf<String>());var room:Room?=null}
class VoiceService:Service(){private var started=false;private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(started)return START_NOT_STICKY;started=true;channel(this,"voice","Group call");startForeground(102,ongoing(this,"Trieps · Voice lounge","Group call aktif · buka aplikasi untuk keluar.","voice"));scope.launch{try{val t=Api(this@VoiceService).obj("tours/${intent?.getLongExtra("tour",0)}/call-token","POST");val room=LiveKit.create(applicationContext);VoiceState.room=room;VoiceState.status="Menghubungkan…";room.connect(t.getString("url"),t.getString("token"));room.localParticipant.setMicrophoneEnabled(true);VoiceState.connected=true;VoiceState.muted=false;VoiceState.status="Terhubung";while(isActive){VoiceState.participants=listOf("Kamu")+room.remoteParticipants.values.map{it.name?:"Rider"};delay(1000)}}catch(e:Exception){VoiceState.status=e.message?:"Panggilan gagal";stopSelf()}};return START_NOT_STICKY}
 override fun onDestroy(){VoiceState.room?.disconnect();VoiceState.room?.release();VoiceState.room=null;VoiceState.connected=false;VoiceState.participants=emptyList();scope.cancel();super.onDestroy()}
 override fun onBind(intent:Intent?)=null
}
class ReminderWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p){override suspend fun doWork():Result{val api=Api(applicationContext);if(api.vault.token.isBlank())return Result.success();return try{val due=api.list("reminders").flatMap{v->v.getJSONArray("reminders").objects().filter{it.optString("status") in listOf("due","soon")}.map{"${v.getJSONObject("vehicle").getString("name")}: ${it.getString("component")}"}};if(due.isNotEmpty()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)){channel(applicationContext,"maintenance","Perawatan kendaraan");val n=NotificationCompat.Builder(applicationContext,"maintenance").setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Waktunya cek kendaraan").setContentText(due.joinToString(" · ")).setStyle(NotificationCompat.BigTextStyle().bigText(due.joinToString("\n"))).setContentIntent(PendingIntent.getActivity(applicationContext,2,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true).build();(applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(201,n)};Result.success()}catch(_:Exception){Result.retry()}}}
