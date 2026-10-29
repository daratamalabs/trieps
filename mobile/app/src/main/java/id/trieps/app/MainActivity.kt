@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package id.trieps.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.credentials.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.*
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

val Forest=Color(0xFF163D32);val Lime=Color(0xFFD3F879);val Paper=Color(0xFFF5F6F0);val Muted=Color(0xFF758078)
class MainActivity:ComponentActivity(){
 var invite by mutableStateOf<String?>(null)
 override fun onCreate(b:Bundle?){super.onCreate(b);invite=intent?.data?.toString();setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Forest,secondary=Forest,tertiary=Lime,background=Paper,surface=Color.White),typography=Typography()){Trieps(viewModel(),invite){invite=null}}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);invite=intent.data?.toString()}
}
class AppModel(app:Application):AndroidViewModel(app){
 val api=Api(app);var signedIn by mutableStateOf(api.vault.token.isNotEmpty());var user by mutableStateOf(JSONObject());var busy by mutableStateOf(false);var error by mutableStateOf<String?>(null)
 var tours by mutableStateOf(listOf<JSONObject>());var vehicles by mutableStateOf(listOf<JSONObject>());var feed by mutableStateOf(listOf<JSONObject>());var communities by mutableStateOf(listOf<JSONObject>());var reminders by mutableStateOf(listOf<JSONObject>())
 var tour by mutableStateOf<JSONObject?>(null);var community by mutableStateOf<JSONObject?>(null);var route by mutableStateOf(listOf<JSONObject>());var members by mutableStateOf(listOf<JSONObject>())
 var serviceRecords by mutableStateOf(listOf<JSONObject>());var serviceReminders by mutableStateOf(listOf<JSONObject>())
 init{if(signedIn)load()}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;viewModelScope.launch{try{block()}catch(e:Exception){error=e.message?:"Terjadi kesalahan. Coba lagi."}finally{busy=false}}}
 fun load(){action{refresh()}}
 suspend fun refresh(){user=api.obj("me");vehicles=api.list("vehicles");tours=api.list("tours");feed=api.obj("posts").getJSONArray("data").objects();communities=api.list("communities");reminders=api.list("reminders");SyncWorker.enqueue(getApplication())}
 fun auth(path:String,data:JSONObject){action{val d=api.obj("auth/$path","POST",data);api.vault.token=d.getString("token");user=d.getJSONObject("user");signedIn=true;refresh()}}
 suspend fun openTour(id:Long){tour=api.obj("tours/$id");members=api.list("tours/$id/members");val remote=api.list("tours/$id/route");val local=TrackStore(getApplication()).use{it.points(id,user.getLong("id"))};route=if(local.size>remote.size)local else remote}
 suspend fun openCommunity(id:Long){community=api.obj("communities/$id")}
 suspend fun openVehicle(id:Long){serviceRecords=api.list("vehicles/$id/services");serviceReminders=api.list("vehicles/$id/reminders")}
}

data class Field(val key:String,val label:String,val initial:String="",val numeric:Boolean=false,val secret:Boolean=false,val options:List<Pair<String,String>> = emptyList())
data class FormSpec(val title:String,val description:String="",val fields:List<Field>,val submit:(Map<String,String>)->Unit)
@Composable fun FormDialog(spec:FormSpec,onDismiss:()->Unit){val values=remember(spec){mutableStateMapOf<String,String>().apply{spec.fields.forEach{put(it.key,it.initial)}}};var validation by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(spec.title,fontWeight=FontWeight.Bold)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){if(spec.description.isNotEmpty())Text(spec.description,color=Muted);spec.fields.forEach{f->if(f.options.isNotEmpty()){var expanded by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(f.label+": "+(f.options.find{it.first==values[f.key]}?.second?:"Pilih"));Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){f.options.forEach{option->DropdownMenuItem(text={Text(option.second)},onClick={values[f.key]=option.first;expanded=false})}}}}else OutlinedTextField(value=values[f.key].orEmpty(),onValueChange={values[f.key]=it},label={Text(f.label)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),visualTransformation=if(f.secret)PasswordVisualTransformation()else VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(f.numeric)KeyboardType.Decimal else KeyboardType.Text))};if(validation.isNotEmpty())Text(validation,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(onClick={if(values.values.any{it.isBlank()})validation="Lengkapi semua kolom." else if(spec.fields.filter{it.numeric}.any{values[it.key]?.toDoubleOrNull()==null})validation="Masukkan angka yang valid." else {onDismiss();spec.submit(values.toMap())}}){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})}
fun Map<String,String>.body(vararg numbers:String)=JSONObject().apply{this@body.forEach{(k,v)->put(k,if(k in numbers)v.toDouble() else v)}}
@Composable fun Primary(text:String,icon:ImageVector=Icons.Outlined.ArrowForward,onClick:()->Unit){Button(onClick=onClick,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=20.dp)){Text(text,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Icon(icon,null)}}
@Composable fun Tile(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
@Composable fun Heading(kicker:String,title:String,subtitle:String=""){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(kicker.uppercase(),color=Forest,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold);Text(title,fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,color=Forest);if(subtitle.isNotBlank())Text(subtitle,color=Muted,fontSize=14.sp)}}
@Composable fun Empty(icon:ImageVector,title:String,desc:String){Tile{Icon(icon,null,tint=Forest,modifier=Modifier.size(32.dp));Text(title,fontWeight=FontWeight.Bold);Text(desc,color=Muted)}}
@Composable fun Badge(text:String){Text(text,Modifier.clip(RoundedCornerShape(50)).background(Lime.copy(alpha=.45f)).padding(horizontal=10.dp,vertical=5.dp),color=Forest,fontSize=11.sp,fontWeight=FontWeight.Bold)}
@Composable fun Stat(value:String,label:String,modifier:Modifier=Modifier){Column(modifier){Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=12.sp,color=Muted)}}
fun display(o:JSONObject,key:String,suffix:String=""):String=if(o.isNull(key)||!o.has(key))"—" else o.optString(key)+suffix

@Composable fun Trieps(vm:AppModel,invite:String?,clearInvite:()->Unit){
 val c=LocalContext.current;val scope=rememberCoroutineScope();var tab by remember{mutableIntStateOf(0)};var form by remember{mutableStateOf<FormSpec?>(null)};var vehicleDetail by remember{mutableStateOf<JSONObject?>(null)};var sharing by remember{mutableStateOf(false)}
 val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true){vm.tour?.let{TrackingService.start(c,it.getLong("id"),vm.user.getLong("id"))}}else vm.error="Izinkan lokasi presisi untuk merekam rute."}
 val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok)vm.tour?.let{ContextCompat.startForegroundService(c,Intent(c,VoiceService::class.java).putExtra("tour",it.getLong("id")))}else vm.error="Izin mikrofon diperlukan untuk group call."}
 val notify=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
 LaunchedEffect(vm.signedIn){if(vm.signedIn&&Build.VERSION.SDK_INT>=33)notify.launch(Manifest.permission.POST_NOTIFICATIONS)}
 fun openForm(title:String,fields:List<Field>,description:String="",submit:(Map<String,String>)->Unit){form=FormSpec(title,description,fields,submit)}
 fun join(kind:String,token:String){if(kind=="community")vm.action{vm.community=vm.api.obj("communities/join/$token","POST");vm.refresh();tab=2}else{if(vm.vehicles.isEmpty()){vm.error="Tambahkan kendaraan di Garasi terlebih dahulu.";return};openForm("Gabung touring",listOf(Field("vehicle_id","Kendaraan",vm.vehicles.first().optString("id"),true,options=vm.vehicles.map{it.optString("id") to it.optString("name")}),Field("start_odo","Odometer awal",vm.vehicles.first().optString("odometer"),true)),vm.vehicles.joinToString{ "${it.optString("id")}: ${it.optString("name")}" }){d->vm.action{val t=vm.api.obj("tours/join/$token","POST",d.body("vehicle_id","start_odo"));vm.openTour(t.getLong("id"));tab=1}}}}
 LaunchedEffect(invite,vm.signedIn){if(invite!=null&&vm.signedIn){val u=android.net.Uri.parse(invite);val paths=u.pathSegments;if(paths.size>=2)join(paths[paths.size-2],paths.last());clearInvite()}}
 LaunchedEffect(vm.tour?.optLong("id"),vm.signedIn){while(vm.signedIn&&vm.tour!=null){delay(10000);val id=vm.tour?.optLong("id")?:break;try{vm.members=vm.api.list("tours/$id/members");val local=TrackStore(c).use{it.points(id,vm.user.getLong("id"))};if(local.isNotEmpty())vm.route=local}catch(_:Exception){/* Keep last known positions while offline. */}}}
 if(!vm.signedIn){AuthScreen(vm);return}
 Scaffold(containerColor=Paper,bottomBar={if(vm.tour==null&&vm.community==null&&vehicleDetail==null&&!sharing)NavigationBar(containerColor=Color.White,tonalElevation=0.dp){listOf("Beranda" to Icons.Outlined.Home,"Touring" to Icons.Outlined.Route,"Komunitas" to Icons.Outlined.Groups,"Garasi" to Icons.Outlined.TwoWheeler).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,p.first)},label={Text(p.first,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(indicatorColor=Lime))}}},topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text("trieps",fontSize=27.sp,fontWeight=FontWeight.Black,color=Forest,letterSpacing=(-1).sp);Spacer(Modifier.weight(1f));if(vm.busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else IconButton(onClick={vm.load()}){Icon(Icons.Outlined.Refresh,"Muat ulang",tint=Forest)};Box(Modifier.size(38.dp).clip(CircleShape).background(Lime),contentAlignment=Alignment.Center){Text(vm.user.optString("name","R").take(1).uppercase(),fontWeight=FontWeight.Bold,color=Forest)}}}){padding->
 Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
 if(vm.tour!=null){val t=vm.tour!!;val id=t.getLong("id");val m=t.getJSONObject("membership");TextButton(onClick={vm.tour=null;sharing=false;vm.load()}){Icon(Icons.Outlined.ArrowBack,null);Text("Semua perjalanan")}
 if(sharing){ShareScreen(vm,t,onClose={sharing=false});return@Column}
 Heading(if(t.optString("mode")=="group")"GROUP TOURING"else"SOLO TOURING",t.getString("title"),"${t.getString("start_name")} → ${t.getString("finish_name")}")
 Badge(if(!m.isNull("finished_at"))"SELESAI" else if(!m.isNull("started_at"))"DALAM PERJALANAN" else "SIAP BERANGKAT")
 RouteMap(vm.route,vm.members)
 if(RideState.tourId==id)Tile{Row{Icon(Icons.Outlined.GpsFixed,null,tint=Forest);Spacer(Modifier.width(8.dp));Text("Perekaman aktif",fontWeight=FontWeight.Bold)};Text(RideState.gpsMessage,color=Muted);Text("${RideState.count} titik sesi ini · otomatis sinkron saat online",fontSize=12.sp)}
 val stats=t.getJSONObject("statistics");Tile{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Stat(display(stats,"distance_km"," km"),"Jarak odometer");Stat(display(stats,"km_per_liter"),"km/l · full-to-full")};Text(stats.getString("note"),fontSize=12.sp,color=Muted)}
 if(m.isNull("finished_at")){
 Primary(if(m.isNull("started_at"))"Mulai & rekam perjalanan" else "Lanjutkan perekaman",Icons.Outlined.PlayArrow){vm.action{vm.api.obj("tours/$id/start","POST");vm.openTour(id);permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}}
 OutlinedButton(onClick={openForm("Catat pengisian BBM",listOf(Field("odometer","Odometer",numeric=true),Field("liters","Liter",numeric=true),Field("cost","Biaya (Rp)","0",true),Field("full","Tangki penuh?","ya",options=listOf("ya" to "Ya, penuh","tidak" to "Pengisian sebagian"))),"Untuk konsumsi akurat, tandai dua pengisian penuh."){d->vm.action{vm.api.obj("tours/$id/fuel","POST",d.filterKeys{it!="full"}.body("odometer","liters","cost").put("full_tank",d["full"].equals("ya",true)));vm.openTour(id)}}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.LocalGasStation,null);Text("  Catat BBM")}
 if(!m.isNull("started_at"))OutlinedButton(onClick={openForm("Selesaikan touring",listOf(Field("finish_odo","Odometer finish",numeric=true)),"Memerlukan koneksi internet. Titik GPS offline tetap tersimpan dan disinkronkan kemudian."){d->vm.action{vm.api.obj("tours/$id/finish","POST",d.body("finish_odo"));TrackingService.stop(c);c.stopService(Intent(c,VoiceService::class.java));vm.openTour(id)}}},modifier=Modifier.fillMaxWidth()){Text("Selesai touring")}
 }else Primary("Buat kartu perjalanan",Icons.Outlined.IosShare){sharing=true}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Checkpoint",fontSize=20.sp,fontWeight=FontWeight.Bold);if(t.getLong("owner_id")==vm.user.getLong("id"))TextButton(onClick={openForm("Tambah checkpoint",listOf(Field("name","Nama tempat"),Field("position","Urutan","${t.getJSONArray("checkpoints").length()+1}",true))){d->vm.action{vm.api.obj("tours/$id/checkpoints","POST",d.body("position"));vm.openTour(id)}}}){Text("+ Tambah")}}
 t.getJSONArray("checkpoints").objects().forEach{cp->val visited=t.getJSONArray("visits").objects().any{it.getLong("checkpoint_id")==cp.getLong("id")};Tile{Row(verticalAlignment=Alignment.CenterVertically){Icon(if(visited)Icons.Outlined.CheckCircle else Icons.Outlined.LocationOn,null,tint=Forest);Text(cp.getString("name"),Modifier.weight(1f).padding(start=8.dp),fontWeight=FontWeight.Bold);if(visited)Badge("TIBA")else if(!m.isNull("started_at")&&m.isNull("finished_at"))TextButton(onClick={openForm("Tiba di ${cp.getString("name")}",listOf(Field("odometer","Odometer checkpoint",numeric=true))){d->vm.action{vm.api.obj("tours/$id/checkpoints/${cp.getLong("id")}/visit","POST",d.body("odometer"));vm.openTour(id)}}}){Text("Tiba")}}}}
 if(t.optString("mode")=="group"){
 Primary("Bagikan undangan",Icons.Outlined.Link){shareText(c,t.getString("invite_url"))}
 Text("Rombongan · ${vm.members.size} rider",fontSize=20.sp,fontWeight=FontWeight.Bold)
 vm.members.forEach{member->val loc=member.optJSONObject("location");Tile{Text(member.getString("name"),fontWeight=FontWeight.Bold);Text(if(loc==null)"Belum mengirim lokasi"else "Lokasi terakhir: ${loc.optString("recorded_at")} UTC",color=Muted,fontSize=12.sp)}}
 Tile{Text("Voice lounge",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Ngobrol langsung dengan rombongan. Memerlukan internet.",color=Muted);Text(VoiceState.status);VoiceState.participants.forEach{Text("● $it")};if(VoiceState.connected){Row{Button(onClick={scope.launch{VoiceState.muted=!VoiceState.muted;VoiceState.room?.localParticipant?.setMicrophoneEnabled(!VoiceState.muted)}}){Text(if(VoiceState.muted)"Buka mic"else"Mute")};TextButton(onClick={c.stopService(Intent(c,VoiceService::class.java))}){Text("Keluar call")}}}else Button(onClick={mic.launch(Manifest.permission.RECORD_AUDIO)}){Text("Gabung call")}}
 }
 Text("Riwayat BBM",fontWeight=FontWeight.Bold);t.getJSONArray("fuel").objects().forEach{f->Tile{Text("${f.optString("liters")} liter · Rp${f.optString("cost")}");Text("ODO ${f.optString("odometer")} km · ${if(f.optInt("full_tank")==1)"Penuh" else "Sebagian"}",color=Muted)}}
 }else if(vm.community!=null){val com=vm.community!!;val id=com.getLong("id");val owner=com.getLong("owner_id")==vm.user.getLong("id");TextButton(onClick={vm.community=null;vm.load()}){Text("← Komunitas")};Heading("RIDE TOGETHER",com.getString("name"),com.optString("description"));val status=com.optString("membership")
 if(owner){Primary("Bagikan undangan",Icons.Outlined.Link){shareText(c,com.getString("invite_url"))};OutlinedButton(onClick={openForm("Undang rider",listOf(Field("email","Email rider terdaftar"))){d->vm.action{vm.api.obj("communities/$id/invite","POST",d.body());vm.openCommunity(id)}}}){Text("Undang lewat email")}}
 if(status=="invited")Primary("Terima undangan"){vm.action{vm.api.obj("communities/$id/accept","POST");vm.openCommunity(id)}}else if(status !in listOf("active","pending"))Primary("Minta bergabung"){vm.action{vm.api.obj("communities/$id/request","POST");vm.openCommunity(id)}}else if(status=="pending")Badge("MENUNGGU PERSETUJUAN")
 com.getJSONArray("members").objects().forEach{member->Tile{Text(member.getString("name"),fontWeight=FontWeight.Bold);Badge(member.getString("status"));if(owner&&member.getString("status")=="pending")Row{TextButton(onClick={vm.action{vm.api.obj("communities/$id/members/${member.getLong("id")}","POST",json("action" to "approve"));vm.openCommunity(id)}}){Text("Terima")};TextButton(onClick={vm.action{vm.api.obj("communities/$id/members/${member.getLong("id")}","POST",json("action" to "reject"));vm.openCommunity(id)}}){Text("Tolak")}}}}
 if(owner){OutlinedButton(onClick={openForm("Transfer kepemilikan",listOf(Field("user_id","Anggota penerima",numeric=true,options=com.getJSONArray("members").objects().filter{it.optString("status")=="active"&&it.getLong("id")!=vm.user.getLong("id")}.map{it.optString("id") to it.optString("name")})),"ID anggota: "+com.getJSONArray("members").objects().joinToString{"${it.optString("id")}: ${it.optString("name")}"}){d->vm.action{vm.api.obj("communities/$id/transfer","POST",d.body("user_id"));vm.openCommunity(id)}}}){Text("Transfer kepemilikan")}}else if(status in listOf("active","invited","pending"))OutlinedButton(onClick={vm.action{vm.api.request("communities/$id/leave","POST");vm.community=null;vm.refresh()}}){Text("Keluar / batalkan permintaan")}
 }else if(vehicleDetail!=null){val v=vehicleDetail!!;val id=v.getLong("id");TextButton(onClick={vehicleDetail=null;vm.load()}){Text("← Garasi")};Heading("MY GARAGE",v.getString("name"),"${v.optString("brand")} ${v.optString("model")} · ${v.optString("year")}")
 Tile{Stat("${v.optString("odometer")} km","Odometer terakhir");TextButton(onClick={openForm("Perbarui odometer",listOf(Field("odometer","Odometer terbaru",v.optString("odometer"),true))){d->vm.action{vehicleDetail=vm.api.obj("vehicles/$id","PATCH",d.body("odometer"));vm.openVehicle(id)}}}){Text("Perbarui kilometer")}}
 Primary("Catat perawatan",Icons.Outlined.Build){openForm("Riwayat perawatan",listOf(Field("component","Komponen (mis. Oli mesin)","Oli mesin"),Field("odometer","Odometer",v.optString("odometer"),true),Field("serviced_at","Tanggal YYYY-MM-DD",java.time.LocalDate.now().toString()),Field("cost","Biaya Rp","0",true),Field("notes","Catatan","Perawatan rutin"))){d->vm.action{vm.api.request("vehicles/$id/services","POST",d.body("odometer","cost"));vm.openVehicle(id);vm.refresh();vehicleDetail=vm.vehicles.find{it.getLong("id")==id}}}}
 OutlinedButton(onClick={openForm("Atur interval servis",listOf(Field("component","Komponen","Oli mesin"),Field("interval_km","Interval kilometer","3000",true),Field("interval_months","Interval bulan","3",true)),"Sesuaikan dengan buku servis kendaraan. Jatuh tempo mengikuti batas yang tercapai lebih dahulu."){d->vm.action{vm.api.request("vehicles/$id/rules","PUT",d.body("interval_km","interval_months"));vm.openVehicle(id)}}},modifier=Modifier.fillMaxWidth()){Text("Atur interval per komponen")}
 vm.serviceReminders.forEach{r->ReminderTile(r)};Text("Riwayat perawatan",fontSize=20.sp,fontWeight=FontWeight.Bold);vm.serviceRecords.forEach{r->Tile{Text(r.getString("component"),fontWeight=FontWeight.Bold);Text("${r.optString("serviced_at")} · ${r.optString("odometer")} km",color=Muted);Text("Rp${r.optString("cost")} · ${r.optString("notes")}")}}
 }else when(tab){
 0->{Heading("LET'S TAKE THE LONG WAY","Hai, ${vm.user.optString("name").substringBefore(' ')} 👋","Cerita berikutnya dimulai dari sini.")
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Forest).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Badge("BUKAN SEKADAR SAMPAI");Text("Jalan lebih jauh.\nPulang bawa cerita.",fontSize=31.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,color=Color.White);RouteSketch(emptyList(),Lime,Modifier.fillMaxWidth().height(110.dp));Button(onClick={tab=1},colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Forest),shape=RoundedCornerShape(16.dp)){Text("Rencanakan touring  ↗",fontWeight=FontWeight.Bold)}}
 if(vm.reminders.any{v->v.getJSONArray("reminders").objects().any{it.optString("status") in listOf("due","soon")}})Tile{Text("Kendaraanmu perlu perhatian",fontWeight=FontWeight.Bold);Text("Ada perawatan yang mendekati jadwal. Cek Garasi sebelum berangkat.",color=Muted);TextButton(onClick={tab=3}){Text("Cek garasi →")}}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Cerita di jalan",fontSize=22.sp,fontWeight=FontWeight.Bold);TextButton(onClick={openForm("Bagikan cerita",listOf(Field("caption","Ceritakan perjalananmu"))){d->vm.action{vm.api.obj("posts","POST",d.body());vm.refresh()}}}){Text("+ Tulis")}}
 PhotoPostComposer(vm)
 if(vm.feed.isEmpty())Empty(Icons.Outlined.Landscape,"Jadilah cerita pertama","Bagikan foto atau kartu rute setelah touring.")
 vm.feed.forEach{p->PostTile(p,onLike={vm.action{vm.api.request("posts/${p.getLong("id")}/like",if(p.optBoolean("liked"))"DELETE"else"PUT");vm.refresh()}},onDelete=if(p.getLong("user_id")==vm.user.getLong("id")){{vm.action{vm.api.request("posts/${p.getLong("id")}","DELETE");vm.refresh()}}}else null)}
 }
 1->{Heading("FIND YOUR NEXT STORY","Perjalananmu","Solo untuk tenang. Bersama untuk kenangan.")
 Primary("Buat touring baru",Icons.Outlined.Add){if(vm.vehicles.isEmpty())vm.error="Tambahkan kendaraan di Garasi terlebih dahulu."else openForm("Touring baru",listOf(Field("title","Nama touring"),Field("mode","Mode touring","solo",options=listOf("solo" to "Solo","group" to "Grup")),Field("start_name","Titik start"),Field("finish_name","Titik finish"),Field("vehicle_id","Kendaraan",vm.vehicles.first().optString("id"),true,options=vm.vehicles.map{it.optString("id") to it.optString("name")}),Field("start_odo","Odometer awal",vm.vehicles.first().optString("odometer"),true)),vm.vehicles.joinToString{"${it.optString("id")}: ${it.optString("name")}"}){d->vm.action{val t=vm.api.obj("tours","POST",d.body("vehicle_id","start_odo"));vm.openTour(t.getLong("id"));vm.refresh()}}}
 OutlinedButton(onClick={openForm("Gabung dari undangan",listOf(Field("link","Link undangan / token"))){d->join("tour",d.getValue("link").trimEnd('/').substringAfterLast('/'))}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Link,null);Text("  Punya undangan touring?")}
 if(vm.tours.isEmpty())Empty(Icons.Outlined.Route,"Belum ada jejak","Buat perjalanan pertama dan simpan setiap kilometernya.")
 vm.tours.forEach{t->Tile(Modifier.clickable{vm.action{vm.openTour(t.getLong("id"))}}){Row{Badge(if(t.optString("mode")=="group")"GROUP"else"SOLO");Spacer(Modifier.weight(1f));Text(t.optString("status"),color=Muted,fontSize=12.sp)};Text(t.getString("title"),fontSize=22.sp,fontWeight=FontWeight.Bold);Text("${t.getString("start_name")} → ${t.getString("finish_name")}",color=Muted);Text("Lihat perjalanan  ↗",color=Forest,fontWeight=FontWeight.Bold)}}
 }
 2->{Heading("GOOD ROADS, BETTER PEOPLE","Komunitas","Temukan teman satu arah, satu cerita.")
 Primary("Buat komunitas",Icons.Outlined.Groups){openForm("Komunitas baru",listOf(Field("name","Nama komunitas"),Field("description","Tentang komunitas"))){d->vm.action{vm.community=vm.api.obj("communities","POST",d.body());vm.refresh()}}}
 OutlinedButton(onClick={openForm("Gabung komunitas",listOf(Field("link","Link undangan / token"))){d->join("community",d.getValue("link").trimEnd('/').substringAfterLast('/'))}},modifier=Modifier.fillMaxWidth()){Text("Gabung lewat undangan")}
 if(vm.communities.isEmpty())Empty(Icons.Outlined.Groups,"Rombonganmu menunggu","Bangun komunitas dan ajak rider lain bergabung.")
 vm.communities.forEach{com->Tile(Modifier.clickable{vm.action{vm.openCommunity(com.getLong("id"))}}){Icon(Icons.Outlined.Groups,null,tint=Forest);Text(com.getString("name"),fontSize=22.sp,fontWeight=FontWeight.Bold);Text(com.optString("description"),color=Muted);Badge("${com.optInt("member_count")} RIDER · ${com.optString("membership").takeUnless{it=="null"}?:"JELAJAHI"}")}}
 }
 3->{Heading("TAKE CARE, GO FAR","Garasimu","Rawat tunggangannya. Nikmati perjalanannya.")
 Primary("Tambah kendaraan",Icons.Outlined.Add){openForm("Kendaraan baru",listOf(Field("name","Nama panggilan"),Field("brand","Merek"),Field("model","Model"),Field("year","Tahun","2024",true),Field("plate","Nomor polisi"),Field("odometer","Odometer saat ini","0",true))){d->vm.action{vm.api.obj("vehicles","POST",d.body("year","odometer"));vm.refresh()}}}
 if(vm.vehicles.isEmpty())Empty(Icons.Outlined.TwoWheeler,"Kenalkan teman jalanmu","Tambahkan kendaraan untuk mulai touring dan mencatat servis.")
 vm.vehicles.forEach{v->Tile(Modifier.clickable{vehicleDetail=v;vm.action{vm.openVehicle(v.getLong("id"))}}){Row{Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Paper),contentAlignment=Alignment.Center){Icon(Icons.Outlined.TwoWheeler,null,tint=Forest,modifier=Modifier.size(36.dp))};Column(Modifier.padding(start=14.dp)){Text(v.getString("name"),fontSize=22.sp,fontWeight=FontWeight.Bold);Text("${v.optString("brand")} ${v.optString("model")}",color=Muted)}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Stat(v.optString("odometer"),"kilometer");Badge(v.optString("plate"))};Text("Perawatan & riwayat →",fontWeight=FontWeight.Bold,color=Forest)}}
 Text("Akun",fontWeight=FontWeight.Bold);Text(vm.user.optString("email"),color=Muted);TextButton(onClick={if(RideState.tourId!=0L||VoiceState.connected)vm.error="Selesaikan perekaman dan keluar call sebelum logout."else vm.action{vm.api.request("auth/logout","POST");vm.api.vault.token="";vm.signedIn=false;vm.tour=null;vm.community=null}}){Text("Keluar akun")}
 }
 }
 }
 }
 form?.let{FormDialog(it){form=null}}
 vm.error?.let{message->AlertDialog(onDismissRequest={vm.error=null},title={Text("Periksa dulu, yuk")},text={Text(message)},confirmButton={TextButton(onClick={vm.error=null}){Text("Mengerti")}})}
}
