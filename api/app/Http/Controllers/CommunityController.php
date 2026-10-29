<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class CommunityController extends Controller
{
    private function owner(Request $r, int $id): object
    {
        $c = DB::table('communities')->find($id);
        abort_unless($c, 404);
        abort_unless($c->owner_id === $r->user()->id, 403);

        return $c;
    }

    public function index(Request $r): Collection
    {
        return DB::table('communities')->orderByDesc('id')->limit(100)->get(['id', 'name', 'description', 'owner_id'])->map(function ($c) use ($r) {
            $c->membership = DB::table('community_members')->where(['community_id' => $c->id, 'user_id' => $r->user()->id])->value('status');
            $c->member_count = DB::table('community_members')->where(['community_id' => $c->id, 'status' => 'active'])->count();

            return $c;
        });
    }

    public function create(Request $r): JsonResponse
    {
        $d = $r->validate(['name' => 'required|string|max:100', 'description' => 'nullable|string|max:2000']);
        $id = DB::transaction(function () use ($d, $r) {
            $id = DB::table('communities')->insertGetId([...$d, 'owner_id' => $r->user()->id, 'invite_token' => Str::random(48), 'created_at' => now(), 'updated_at' => now()]);
            DB::table('community_members')->insert(['community_id' => $id, 'user_id' => $r->user()->id, 'status' => 'active']);

            return $id;
        });

        return response()->json($this->show($r, $id), 201);
    }

    public function show(Request $r, int $id): array
    {
        $c = DB::table('communities')->find($id);
        abort_unless($c, 404);
        $owner = $c->owner_id === $r->user()->id;
        $m = DB::table('community_members')->where(['community_id' => $id, 'user_id' => $r->user()->id])->value('status');
        $members = [];
        if ($owner || $m === 'active') {
            $members = DB::table('community_members')->join('users', 'users.id', '=', 'community_members.user_id')->where('community_id', $id)->when(! $owner, fn ($q) => $q->where('status', 'active'))->get(['users.id', 'users.name', 'community_members.status']);
        }

        return ['id' => $c->id, 'name' => $c->name, 'description' => $c->description, 'owner_id' => $c->owner_id, 'membership' => $m, 'members' => $members, 'invite_url' => $owner ? config('trieps.invite_base').'/invite/community/'.$c->invite_token : null];
    }

    public function request(Request $r, int $id): array
    {
        abort_unless(DB::table('communities')->find($id), 404);
        DB::table('community_members')->insertOrIgnore(['community_id' => $id, 'user_id' => $r->user()->id, 'status' => 'pending']);

        return $this->show($r, $id);
    }

    public function join(Request $r, string $token): array
    {
        $c = DB::table('communities')->where('invite_token', $token)->first();
        abort_unless($c, 404);
        DB::table('community_members')->updateOrInsert(['community_id' => $c->id, 'user_id' => $r->user()->id], ['status' => 'active']);

        return $this->show($r, $c->id);
    }

    public function invite(Request $r, int $id): array
    {
        $this->owner($r, $id);
        $d = $r->validate(['email' => 'required|email']);
        $u = DB::table('users')->where('email', $d['email'])->first();
        abort_unless($u, 404, 'User belum terdaftar.');
        DB::table('community_members')->insertOrIgnore(['community_id' => $id, 'user_id' => $u->id, 'status' => 'invited']);

        return $this->show($r, $id);
    }

    public function accept(Request $r, int $id): array
    {
        $n = DB::table('community_members')->where(['community_id' => $id, 'user_id' => $r->user()->id, 'status' => 'invited'])->update(['status' => 'active']);
        abort_unless($n, 409);

        return $this->show($r, $id);
    }

    public function moderate(Request $r, int $id, int $user): array
    {
        $this->owner($r, $id);
        $d = $r->validate(['action' => 'required|in:approve,reject']);
        $q = DB::table('community_members')->where(['community_id' => $id, 'user_id' => $user, 'status' => 'pending']);
        if ($d['action'] === 'approve') {
            $q->update(['status' => 'active']);
        } else {
            $q->delete();
        }

        return $this->show($r, $id);
    }

    public function leave(Request $r, int $id): Response
    {
        $c = DB::table('communities')->find($id);
        abort_unless($c, 404);
        abort_if($c->owner_id === $r->user()->id, 409, 'Pemilik perlu mentransfer kepemilikan sebelum keluar.');
        DB::table('community_members')->where(['community_id' => $id, 'user_id' => $r->user()->id])->delete();

        return response()->noContent();
    }

    public function transfer(Request $r, int $id): array
    {
        $this->owner($r, $id);
        $d = $r->validate(['user_id' => 'required|integer']);
        abort_unless(DB::table('community_members')->where(['community_id' => $id, 'user_id' => $d['user_id'], 'status' => 'active'])->exists(), 422);
        DB::table('communities')->where('id', $id)->update(['owner_id' => $d['user_id']]);

        return $this->show($r, $id);
    }

    public function rotate(Request $r, int $id): array
    {
        $this->owner($r, $id);
        DB::table('communities')->where('id', $id)->update(['invite_token' => Str::random(48)]);

        return $this->show($r, $id);
    }
}
