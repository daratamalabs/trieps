package id.trieps.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.credentials.*
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch
import org.json.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import java.io.File

fun shareText(c:Context,text:String){c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Bagikan dengan"))}
@Composable fun AuthScreen(vm:AppModel){
 val c=LocalContext.current;val scope=rememberCoroutineScope();var register by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var visible by remember{mutableStateOf(false)};var notice by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 Text("trieps",fontSize=38.sp,fontWeight=FontWeight.Black,color=Forest,letterSpacing=(-2).sp)
 Spacer(Modifier.height(20.dp));Heading("MADE FOR THE JOURNEY",if(register)"Cerita barumu\ndimulai di sini."else"Jalanan memanggil.\nSiap berangkat?","Satu tempat untuk perjalanan, teman, dan kendaraanmu.")
 RouteSketch(emptyList(),Forest,Modifier.fillMaxWidth().height(130.dp))
 if(register)OutlinedTextField(name,{name=it},label={Text("Nama rider")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
 OutlinedTextField(email,{email=it},label={Text("Email")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
 OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(Icons.Outlined.Visibility,"Tampilkan password")}})
 if(register)Text("Gunakan minimal 10 karakter.",color=Muted,fontSize=12.sp)
 Button(onClick={vm.auth(if(register)"register"else"login",json("name" to name,"email" to email.trim(),"password" to password))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)){Text(if(vm.busy)"Sebentar…"else if(register)"Buat akun"else"Masuk & jelajahi",fontWeight=FontWeight.Bold)}
 OutlinedButton(onClick={if(BuildConfig.GOOGLE_CLIENT_ID.isBlank())notice="Google login belum dikonfigurasi oleh pengelola."else scope.launch{try{val option=GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_CLIENT_ID).build();val result=CredentialManager.create(c).getCredential(c,GetCredentialRequest.Builder().addCredentialOption(option).build());val token=GoogleIdTokenCredential.createFrom(result.credential.data).idToken;vm.auth("google",json("id_token" to token))}catch(e:Exception){notice=e.message?:"Google login dibatalkan"}}},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp)){Text("Lanjutkan dengan Google")}
 TextButton(onClick={register=!register}){Text(if(register)"Sudah punya akun? Masuk"else"Belum punya akun? Daftar")}
 if(!register)TextButton(onClick={if(email.isBlank())notice="Isi email kamu terlebih dahulu."else vm.action{vm.api.obj("auth/forgot-password","POST",json("email" to email.trim()));notice="Jika email terdaftar, tautan reset akan dikirim."}}){Text("Lupa password?")}
 Text("Dengan melanjutkan, kamu dapat merekam lokasi hanya saat memilih mulai touring. Kamu mengontrol apa yang dibagikan.",fontSize=12.sp,color=Muted)
 if(notice.isNotBlank())Text(notice,color=Forest)
 vm.error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={vm.error=null}){Text("Tutup")}}
 }
}
@Composable fun ReminderTile(r:JSONObject){Tile{Text(r.getString("component"),fontWeight=FontWeight.Bold);Badge(when(r.optString("status")){"due"->"SUDAH JATUH TEMPO";"soon"->"SEGERA SERVIS";"ok"->"MASIH AMAN";else->"RIWAYAT BELUM ADA"});if(r.has("next_date"))Text("Berikutnya ${r.optString("next_odometer")} km atau ${r.optString("next_date")}",color=Muted)else Text("Tambahkan riwayat agar jadwal dapat dihitung.",color=Muted)}}
@Composable fun RouteMap(points:List<JSONObject>,members:List<JSONObject>){
 val c=LocalContext.current
 val map=remember{MapView(c).apply{setMultiTouchControls(true);controller.setZoom(11.0);controller.setCenter(GeoPoint(-6.9175,107.6191))}}
 DisposableEffect(map){map.onResume();onDispose{map.onPause();map.onDetach()}}
 AndroidView(factory={map},modifier=Modifier.fillMaxWidth().height(290.dp),update={m->m.overlays.clear();if(points.isNotEmpty()){val line=Polyline();line.setPoints(points.map{GeoPoint(it.optDouble("latitude"),it.optDouble("longitude"))});line.outlinePaint.color=android.graphics.Color.rgb(22,61,50);line.outlinePaint.strokeWidth=9f;m.overlays.add(line);m.controller.setCenter(GeoPoint(points.last().optDouble("latitude"),points.last().optDouble("longitude")))};members.forEach{member->member.optJSONObject("location")?.let{l->m.overlays.add(Marker(m).apply{position=GeoPoint(l.optDouble("latitude"),l.optDouble("longitude"));title=member.optString("name");snippet="Terakhir: ${l.optString("recorded_at")} UTC";setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)})}};m.invalidate()})
 Text("© OpenStreetMap contributors · Peta memerlukan internet; GPS tetap direkam saat offline.",fontSize=10.sp,color=Muted)
}
fun normalized(points:List<JSONObject>):List<Pair<Float,Float>>{
 if(points.isEmpty())return emptyList()
 val minLat=points.minOf{it.optDouble("latitude")};val minLon=points.minOf{it.optDouble("longitude")};val span=maxOf(points.maxOf{it.optDouble("latitude")}-minLat,points.maxOf{it.optDouble("longitude")}-minLon,0.00001)
 return points.map{((it.optDouble("longitude")-minLon)/span).toFloat() to (1-(it.optDouble("latitude")-minLat)/span).toFloat()}
}
@Composable fun RouteSketch(shape:List<Pair<Float,Float>>,color:Color,modifier:Modifier=Modifier){Canvas(modifier){val p=if(shape.size>=2)shape else listOf(.08f to .8f,.2f to .72f,.24f to .4f,.42f to .49f,.37f to .2f,.6f to .15f,.69f to .4f,.85f to .25f,.94f to .3f);val path=Path();p.forEachIndexed{i,(x,y)->val xx=16+x*(size.width-32);val yy=16+y*(size.height-32);if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)};drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(width=6f,cap=StrokeCap.Round,join=StrokeJoin.Round));val a=p.first();val b=p.last();drawCircle(color,7f,Offset(16+a.first*(size.width-32),16+a.second*(size.height-32)));drawCircle(color,9f,Offset(16+b.first*(size.width-32),16+b.second*(size.height-32)))}}
@Composable fun PostTile(p:JSONObject,onLike:()->Unit,onDelete:(()->Unit)?){Tile{Row{Text(p.getString("name"),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(p.optString("created_at").take(10),color=Muted,fontSize=11.sp)};Text(p.getString("caption"));p.optJSONObject("route_card")?.let{card->val shape=card.optJSONArray("shape")?:JSONArray();val coords=(0 until shape.length()).map{shape.getJSONArray(it).let{p->p.getDouble(0).toFloat() to (1-p.getDouble(1)).toFloat()}};val bg=card.optString("background");Column(Modifier.fillMaxWidth().background(when(bg){"sand"->Color(0xFFF0E4CB);"night"->Color(0xFF172326);else->Forest}).padding(16.dp)){Text("TRIEPS / THE LONG WAY",color=if(bg=="sand")Forest else Lime,fontSize=11.sp,letterSpacing=2.sp);if(coords.size>=2)RouteSketch(coords,if(bg=="sand")Forest else Lime,Modifier.fillMaxWidth().height(190.dp));Text(listOfNotNull(if(!card.isNull("distance_km"))"${card.optString("distance_km")} km"else null,if(!card.isNull("km_per_liter"))"${card.optString("km_per_liter")} km/l"else null).joinToString("   ·   "),color=if(bg=="sand")Forest else Color.White)}}
 val base=BuildConfig.API_URL.substringBefore("/api/");p.optJSONArray("photos")?.let{photos->for(i in 0 until photos.length())AsyncImage(model="$base/storage/${photos.getString(i)}",contentDescription="Foto touring",modifier=Modifier.fillMaxWidth().height(230.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
 Row{TextButton(onClick=onLike){Icon(if(p.optBoolean("liked"))Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Suka");Text(" ${p.optInt("likes")}")};Spacer(Modifier.weight(1f));onDelete?.let{TextButton(onClick=it){Text("Hapus")}}}}}
@Composable fun PhotoPostComposer(vm:AppModel){var photos by remember{mutableStateOf(listOf<android.net.Uri>())};var caption by remember{mutableStateOf("")};val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){photos=it.take(10)};Tile{Row{Icon(Icons.Outlined.AddPhotoAlternate,null,tint=Forest);TextButton(onClick={picker.launch("image/*")}){Text(if(photos.isEmpty())"Bagikan foto touring"else"${photos.size} foto dipilih")}};if(photos.isNotEmpty()){OutlinedTextField(caption,{caption=it},label={Text("Tulis cerita fotomu")},modifier=Modifier.fillMaxWidth());Button(onClick={vm.action{val paths=photos.map{vm.api.upload(it)};vm.api.obj("posts","POST",json("caption" to caption,"photos" to JSONArray(paths)));photos=emptyList();caption="";vm.refresh()}},enabled=caption.isNotBlank()&&!vm.busy){Text("Posting foto")}}}}
@Composable fun ShareScreen(vm:AppModel,t:JSONObject,onClose:()->Unit){
 val c=LocalContext.current;var bg by remember{mutableStateOf("forest")};var distance by remember{mutableStateOf(true)};var fuel by remember{mutableStateOf(false)};var caption by remember{mutableStateOf(t.getString("title"))};val shape=normalized(vm.route);val color=if(bg=="sand")Forest else Lime;val background=when(bg){"sand"->Color(0xFFEADFC9);"night"->Color(0xFF172326);else->Forest}
 Heading("YOUR RIDE, YOUR STORY","Satu rute. Banyak cerita.","Pilih tampilan dan data yang ingin kamu bagikan.")
 Column(Modifier.fillMaxWidth().background(background,RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){Text("TRIEPS / RIDE RECAP",color=color,fontSize=12.sp,letterSpacing=2.sp);Text(t.getString("title"),color=color,fontWeight=FontWeight.Bold,fontSize=28.sp);if(shape.size>=2)RouteSketch(shape,color,Modifier.fillMaxWidth().height(260.dp))else Text("Rute GPS belum tersedia. Sinkronkan titik perjalanan terlebih dahulu.",color=color);Text(shareStats(t,distance,fuel),color=color,fontSize=22.sp)}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("forest","night","sand").forEach{b->FilterChip(selected=bg==b,onClick={bg=b},label={Text(b.replaceFirstChar{it.uppercase()})})}}
 Row{Checkbox(distance,{distance=it});Text("Jarak",Modifier.padding(top=13.dp));Checkbox(fuel,{fuel=it});Text("Konsumsi BBM",Modifier.padding(top=13.dp))}
 OutlinedTextField(caption,{caption=it},label={Text("Caption")},modifier=Modifier.fillMaxWidth())
 Primary("Bagikan gambar",Icons.Outlined.IosShare){runCatching{shareCard(c,t,shape,bg,distance,fuel)}.onFailure{vm.error=it.message}}
 Primary("Posting ke timeline",Icons.Outlined.Public){vm.action{vm.api.obj("posts","POST",json("tour_id" to t.getLong("id"),"caption" to caption,"route_card" to json("background" to bg,"show_distance" to distance,"show_fuel" to fuel)));vm.refresh();onClose()}}
}
fun shareStats(t:JSONObject,distance:Boolean,fuel:Boolean):String{val s=t.getJSONObject("statistics");return listOfNotNull(if(distance)display(s,"distance_km"," km")else null,if(fuel)display(s,"km_per_liter"," km/l")else null).joinToString("   ·   ")}
fun shareCard(c:Context,t:JSONObject,shape:List<Pair<Float,Float>>,bg:String,distance:Boolean,fuel:Boolean){
 val bitmap=Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888);val canvas=NativeCanvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG);canvas.drawColor(android.graphics.Color.parseColor(when(bg){"sand"->"#EADFC9";"night"->"#172326";else->"#163D32"}));paint.color=android.graphics.Color.parseColor(if(bg=="sand")"#163D32"else"#D3F879");paint.textSize=32f;canvas.drawText("TRIEPS  /  THE LONG WAY",80f,110f,paint);paint.textSize=62f;paint.isFakeBoldText=true;canvas.drawText(t.getString("title").take(25),80f,230f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=12f;paint.strokeCap=Paint.Cap.ROUND;val path=android.graphics.Path();shape.forEachIndexed{i,p->val x=100+p.first*880;val y=340+p.second*700;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};canvas.drawPath(path,paint);paint.style=Paint.Style.FILL;paint.textSize=46f;canvas.drawText(shareStats(t,distance,fuel),80f,1190f,paint);paint.textSize=26f;paint.isFakeBoldText=false;canvas.drawText("Setiap kilometer punya cerita.",80f,1260f,paint);val dir=File(c.cacheDir,"shares").apply{mkdirs()};val f=File(dir,"trieps-${t.getLong("id")}.png");f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};val uri=FileProvider.getUriForFile(c,"${c.packageName}.files",f);c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Bagikan perjalanan"));bitmap.recycle()
}
