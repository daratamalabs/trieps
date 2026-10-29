<?php

use App\Http\Controllers\AuthController as Auth;
use App\Http\Controllers\CommunityController as Community;
use App\Http\Controllers\FeedController as Feed;
use App\Http\Controllers\GarageController as Garage;
use App\Http\Controllers\TourController as Tour;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function () {
    Route::get('health', fn () => ['app' => 'Trieps', 'status' => 'ok']);
    Route::middleware('throttle:10,1')->group(function () {
        Route::post('auth/register', [Auth::class, 'register']);
        Route::post('auth/login', [Auth::class, 'login']);
        Route::post('auth/google', [Auth::class, 'google']);
        Route::post('auth/forgot-password', [Auth::class, 'forgot']);
        Route::post('auth/reset-password', [Auth::class, 'reset']);
    });
    Route::middleware(['auth:sanctum', 'throttle:180,1'])->group(function () {
        Route::get('me', fn (Request $r) => $r->user());
        Route::post('auth/logout', [Auth::class, 'logout']);
        Route::get('vehicles', [Garage::class, 'index']);
        Route::post('vehicles', [Garage::class, 'create']);
        Route::patch('vehicles/{id}', [Garage::class, 'update']);
        Route::get('reminders', [Garage::class, 'allReminders']);
        Route::get('vehicles/{id}/services', [Garage::class, 'records']);
        Route::post('vehicles/{id}/services', [Garage::class, 'record']);
        Route::put('vehicles/{id}/rules', [Garage::class, 'rules']);
        Route::get('vehicles/{id}/reminders', [Garage::class, 'reminders']);
        Route::get('tours', [Tour::class, 'index']);
        Route::post('tours', [Tour::class, 'create']);
        Route::post('tours/join/{token}', [Tour::class, 'join']);
        Route::get('tours/{id}', [Tour::class, 'show']);
        Route::post('tours/{id}/start', [Tour::class, 'start']);
        Route::post('tours/{id}/finish', [Tour::class, 'finish']);
        Route::post('tours/{id}/rotate-invite', [Tour::class, 'rotate']);
        Route::post('tours/{id}/checkpoints', [Tour::class, 'checkpoint']);
        Route::post('tours/{id}/checkpoints/{cp}/visit', [Tour::class, 'visit']);
        Route::post('tours/{id}/points', [Tour::class, 'points']);
        Route::get('tours/{id}/route', [Tour::class, 'route']);
        Route::get('tours/{id}/members', [Tour::class, 'members']);
        Route::post('tours/{id}/fuel', [Tour::class, 'fuel']);
        Route::post('tours/{id}/call-token', [Tour::class, 'call']);
        Route::get('communities', [Community::class, 'index']);
        Route::post('communities', [Community::class, 'create']);
        Route::post('communities/join/{token}', [Community::class, 'join']);
        Route::get('communities/{id}', [Community::class, 'show']);
        Route::post('communities/{id}/request', [Community::class, 'request']);
        Route::post('communities/{id}/invite', [Community::class, 'invite']);
        Route::post('communities/{id}/accept', [Community::class, 'accept']);
        Route::post('communities/{id}/leave', [Community::class, 'leave']);
        Route::post('communities/{id}/members/{user}', [Community::class, 'moderate']);
        Route::post('communities/{id}/transfer', [Community::class, 'transfer']);
        Route::post('communities/{id}/rotate-invite', [Community::class, 'rotate']);
        Route::get('posts', [Feed::class, 'index']);
        Route::post('posts', [Feed::class, 'create']);
        Route::delete('posts/{id}', [Feed::class, 'delete']);
        Route::post('photos', [Feed::class, 'upload']);
        Route::put('posts/{id}/like', [Feed::class, 'like']);
        Route::delete('posts/{id}/like', [Feed::class, 'unlike']);
    });
});
