<?php

namespace App\Http\Controllers;

use App\Models\User;
use Google\Client;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    private function session(User $user): array
    {
        return ['user' => $user, 'token' => $user->createToken('android', ['*'], now()->addDays(30))->plainTextToken];
    }

    public function register(Request $r): JsonResponse
    {
        $d = $r->validate(['name' => 'required|string|max:80', 'email' => 'required|email|max:255|unique:users', 'password' => 'required|string|min:10|max:128']);
        $d['password'] = Hash::make($d['password']);

        return response()->json($this->session(User::create($d)), 201);
    }

    public function login(Request $r): array
    {
        $d = $r->validate(['email' => 'required|email', 'password' => 'required|string']);
        $u = User::where('email', $d['email'])->first();
        if (! $u || ! Hash::check($d['password'], $u->password)) {
            throw ValidationException::withMessages(['email' => 'Email atau password tidak sesuai.']);
        }

        return $this->session($u);
    }

    public function google(Request $r): array
    {
        $d = $r->validate(['id_token' => 'required|string']);
        abort_unless(config('trieps.google_client_id'), 503, 'Google login belum dikonfigurasi.');
        $g = new Client(['client_id' => config('trieps.google_client_id')]);
        try {
            $p = $g->verifyIdToken($d['id_token']);
        } catch (\Throwable) {
            $p = false;
        }abort_unless($p && ($p['email_verified'] ?? false), 401, 'Token Google tidak valid.');
        $u = User::where('google_id', $p['sub'])->first();
        if (! $u) {
            $u = User::where('email', $p['email'])->first();
            abort_if($u, 409, 'Email sudah terdaftar. Masuk dengan email untuk keamanan akun.');
            $u = User::create(['name' => $p['name'] ?? 'Rider', 'email' => $p['email'], 'password' => Hash::make(Str::random(64))]);
            $u->google_id = $p['sub'];
            $u->email_verified_at = now();
            $u->save();
        }

        return $this->session($u);
    }

    public function forgot(Request $r): array
    {
        $r->validate(['email' => 'required|email']);
        Password::sendResetLink($r->only('email'));

        return ['message' => 'Jika email terdaftar, tautan reset akan dikirim.'];
    }

    public function reset(Request $r): array
    {
        $r->validate(['token' => 'required', 'email' => 'required|email', 'password' => 'required|min:10|confirmed']);
        $s = Password::reset($r->only('email', 'password', 'password_confirmation', 'token'), function (User $u, string $p) {
            $u->forceFill(['password' => Hash::make($p), 'remember_token' => Str::random(60)])->save();
            $u->tokens()->delete();
        });
        abort_unless($s === Password::PasswordReset, 422, __($s));

        return ['message' => 'Password diperbarui.'];
    }

    public function logout(Request $r): Response
    {
        $r->user()->currentAccessToken()->delete();

        return response()->noContent();
    }
}
