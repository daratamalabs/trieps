<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Route;

Route::get('/', fn () => view('home'));
Route::get('/privacy', fn () => view('privacy'));
Route::get('/invite/{kind}/{token}', function (string $kind, string $token) {
    abort_unless(in_array($kind, ['tour', 'community']) && preg_match('/^[a-zA-Z0-9]{48}$/', $token), 404);

    return view('invite', compact('kind', 'token'));
});
Route::post('/waitlist', function (Request $r) {
    $d = $r->validate(['email' => 'required|email|max:255']);
    DB::table('waitlist')->insertOrIgnore([...$d, 'created_at' => now(), 'updated_at' => now()]);

    return back()->with('success', 'Kamu masuk daftar! Kami akan mengabari saat Trieps siap.');
})->middleware('throttle:5,1');
