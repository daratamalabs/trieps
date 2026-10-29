<?php

use Illuminate\Support\Facades\Route;

Route::get('/', fn () => response()->json(['app' => 'Trieps API', 'version' => 'v1']));
Route::get('/reset-password/{token}', function (string $token) {
    return view('reset', ['token' => $token, 'email' => request('email')]);
})->name('password.reset');
