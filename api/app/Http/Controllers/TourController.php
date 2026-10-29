<?php

namespace App\Http\Controllers;

use Carbon\Carbon;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class TourController extends Controller
{
    public static function member(Request $r, int $id): object
    {
        $m = DB::table('tour_members')->where(['tour_id' => $id, 'user_id' => $r->user()->id])->first();
        abort_unless($m, 403);

        return $m;
    }

    private function owned(Request $r, int $id): object
    {
        $t = DB::table('tours')->find($id);
        abort_unless($t, 404);
        abort_unless($t->owner_id === $r->user()->id, 403);

        return $t;
    }

    public function index(Request $r): Collection
    {
        return DB::table('tours')->join('tour_members', 'tours.id', '=', 'tour_members.tour_id')->where('tour_members.user_id', $r->user()->id)->select('tours.*', 'tour_members.vehicle_id', 'tour_members.start_odo', 'tour_members.finish_odo', 'tour_members.started_at', 'tour_members.finished_at')->orderByDesc('tours.id')->get();
    }

    public function create(Request $r): JsonResponse
    {
        $d = $r->validate(['title' => 'required|string|max:120', 'mode' => 'required|in:solo,group', 'start_name' => 'required|string|max:120', 'finish_name' => 'required|string|max:120', 'vehicle_id' => 'required|integer', 'start_odo' => 'required|numeric|min:0']);
        $v = DB::table('vehicles')->where('user_id', $r->user()->id)->find($d['vehicle_id']);
        abort_unless($v, 404);
        abort_if($d['start_odo'] < $v->odometer, 422, 'Odometer di bawah catatan kendaraan.');
        $id = DB::transaction(function () use ($r, $d) {
            $id = DB::table('tours')->insertGetId(['owner_id' => $r->user()->id, 'title' => $d['title'], 'mode' => $d['mode'], 'status' => 'planned', 'start_name' => $d['start_name'], 'finish_name' => $d['finish_name'], 'invite_token' => Str::random(48), 'created_at' => now(), 'updated_at' => now()]);
            DB::table('tour_members')->insert(['tour_id' => $id, 'user_id' => $r->user()->id, 'vehicle_id' => $d['vehicle_id'], 'start_odo' => $d['start_odo']]);

            return $id;
        });

        return response()->json($this->show($r, $id), 201);
    }

    public function show(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $t = (array) DB::table('tours')->find($id);
        $t['membership'] = $m;
        $t['checkpoints'] = DB::table('checkpoints')->where('tour_id', $id)->orderBy('position')->get();
        $t['fuel'] = DB::table('fuel_entries')->where(['tour_id' => $id, 'user_id' => $r->user()->id])->orderBy('odometer')->get();
        $t['visits'] = DB::table('checkpoint_visits')->join('checkpoints', 'checkpoints.id', '=', 'checkpoint_visits.checkpoint_id')->where(['checkpoints.tour_id' => $id, 'checkpoint_visits.user_id' => $r->user()->id])->select('checkpoint_visits.*')->get();
        $t['invite_url'] = config('trieps.invite_base').'/invite/tour/'.$t['invite_token'];
        $t['statistics'] = $this->statistics($r, $id);

        return $t;
    }

    public function join(Request $r, string $token): array
    {
        $d = $r->validate(['vehicle_id' => 'required|integer', 'start_odo' => 'required|numeric|min:0']);
        $t = DB::table('tours')->where('invite_token', $token)->first();
        abort_unless($t && $t->mode === 'group', 404);
        abort_if($t->status === 'finished', 409, 'Touring telah selesai.');
        abort_unless(DB::table('vehicles')->where('user_id', $r->user()->id)->where('odometer', '<=', $d['start_odo'])->find($d['vehicle_id']), 422, 'Kendaraan atau odometer tidak valid.');
        DB::table('tour_members')->insertOrIgnore(['tour_id' => $t->id, 'user_id' => $r->user()->id, 'vehicle_id' => $d['vehicle_id'], 'start_odo' => $d['start_odo']]);

        return $this->show($r, $t->id);
    }

    public function rotate(Request $r, int $id): array
    {
        $this->owned($r, $id);
        DB::table('tours')->where('id', $id)->update(['invite_token' => Str::random(48)]);

        return $this->show($r, $id);
    }

    public function start(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        abort_if($m->finished_at, 409);
        DB::table('tour_members')->where('id', $m->id)->whereNull('started_at')->update(['started_at' => now()]);
        DB::table('tours')->where('id', $id)->where('status', 'planned')->update(['status' => 'active']);

        return $this->show($r, $id);
    }

    public function checkpoint(Request $r, int $id): array
    {
        $this->owned($r, $id);
        $d = $r->validate(['name' => 'required|string|max:120', 'latitude' => 'nullable|numeric|between:-90,90', 'longitude' => 'nullable|numeric|between:-180,180', 'position' => 'required|integer|min:0']);
        $d['tour_id'] = $id;

        return ['id' => DB::table('checkpoints')->insertGetId($d)];
    }

    public function visit(Request $r, int $id, int $cp): array
    {
        $m = self::member($r, $id);
        abort_unless($m->started_at && ! $m->finished_at, 409);
        abort_unless(DB::table('checkpoints')->where('tour_id', $id)->find($cp), 404);
        $d = $r->validate(['odometer' => 'required|numeric|min:'.$m->start_odo]);
        DB::table('checkpoint_visits')->updateOrInsert(['checkpoint_id' => $cp, 'user_id' => $r->user()->id], ['odometer' => $d['odometer'], 'visited_at' => now()]);

        return ['message' => 'Checkpoint tersimpan.'];
    }

    public function points(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $d = $r->validate(['points' => 'required|array|min:1|max:500', 'points.*.client_id' => 'required|uuid', 'points.*.latitude' => 'required|numeric|between:-90,90', 'points.*.longitude' => 'required|numeric|between:-180,180', 'points.*.accuracy' => 'required|numeric|between:0,1000', 'points.*.recorded_at' => 'required|date']);
        abort_unless($m->started_at, 409, 'Touring belum dimulai.');
        $ids = [];
        DB::transaction(function () use ($d, $m, $id, $r, &$ids) {
            foreach ($d['points'] as $p) {
                $time = Carbon::parse($p['recorded_at'])->utc();
                abort_if($time->gt(now()->addMinutes(5)) || $time->lt(Carbon::parse($m->started_at)->subMinutes(5)) || ($m->finished_at && $time->gt(Carbon::parse($m->finished_at)->addMinutes(1))), 422, 'Waktu titik di luar perjalanan.');
                $p['recorded_at'] = $time->format('Y-m-d H:i:s');
                DB::table('track_points')->insertOrIgnore([...$p, 'tour_id' => $id, 'user_id' => $r->user()->id]);
                $ids[] = $p['client_id'];
            }
        });

        return ['accepted' => $ids];
    }

    public function route(Request $r, int $id): Collection
    {
        self::member($r, $id);

        return DB::table('track_points')->where(['tour_id' => $id, 'user_id' => $r->user()->id])->orderBy('recorded_at')->get(['latitude', 'longitude', 'recorded_at']);
    }

    public function members(Request $r, int $id): Collection
    {
        self::member($r, $id);

        return DB::table('tour_members')->join('users', 'users.id', '=', 'tour_members.user_id')->where('tour_id', $id)->select('users.id', 'users.name', 'tour_members.finished_at')->get()->map(function ($m) use ($id) {
            $m->location = DB::table('track_points')->where(['tour_id' => $id, 'user_id' => $m->id])->orderByDesc('recorded_at')->first(['latitude', 'longitude', 'recorded_at']);

            return $m;
        });
    }

    public function fuel(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $d = $r->validate(['odometer' => 'required|numeric|min:'.$m->start_odo, 'liters' => 'required|numeric|gt:0|max:1000', 'cost' => 'required|numeric|min:0', 'full_tank' => 'required|boolean']);
        abort_if($m->finish_odo && $d['odometer'] > $m->finish_odo, 422);
        $entry = DB::table('fuel_entries')->insertGetId([...$d, 'tour_id' => $id, 'user_id' => $r->user()->id, 'created_at' => now(), 'updated_at' => now()]);

        return ['id' => $entry, 'statistics' => $this->statistics($r, $id)];
    }

    public function finish(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $d = $r->validate(['finish_odo' => 'required|numeric|min:'.$m->start_odo]);
        abort_unless($m->started_at, 409);
        $max = max(DB::table('fuel_entries')->where(['tour_id' => $id, 'user_id' => $r->user()->id])->max('odometer') ?? 0, DB::table('checkpoint_visits')->join('checkpoints', 'checkpoints.id', '=', 'checkpoint_visits.checkpoint_id')->where(['checkpoints.tour_id' => $id, 'checkpoint_visits.user_id' => $r->user()->id])->max('odometer') ?? 0);
        abort_if($d['finish_odo'] < $max, 422, 'Odometer finish di bawah catatan terakhir.');
        DB::transaction(function () use ($m, $d, $id) {
            DB::table('tour_members')->where('id', $m->id)->update(['finish_odo' => $d['finish_odo'], 'finished_at' => now()]);
            DB::table('vehicles')->where('id', $m->vehicle_id)->where('odometer', '<', $d['finish_odo'])->update(['odometer' => $d['finish_odo']]);
            if (! DB::table('tour_members')->where('tour_id', $id)->whereNull('finished_at')->exists()) {
                DB::table('tours')->where('id', $id)->update(['status' => 'finished']);
            }
        });

        return $this->show($r, $id);
    }

    public function statistics(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $f = DB::table('fuel_entries')->where(['tour_id' => $id, 'user_id' => $r->user()->id])->orderBy('odometer')->orderBy('id')->get();
        $distance = $m->finish_odo !== null ? (float) $m->finish_odo - (float) $m->start_odo : null;
        $anchor = null;
        $liters = 0.;
        $covered = 0.;
        $consumed = 0.;
        foreach ($f as $e) {
            if ($anchor !== null) {
                $liters += (float) $e->liters;
            }if ($e->full_tank) {
                if ($anchor !== null && $e->odometer > $anchor) {
                    $covered += (float) $e->odometer - $anchor;
                    $consumed += $liters;
                }$anchor = (float) $e->odometer;
                $liters = 0.;
            }
        }

        return ['distance_km' => $distance, 'purchased_liters' => (float) $f->sum('liters'), 'fuel_cost' => (float) $f->sum('cost'), 'measured_distance_km' => $covered, 'km_per_liter' => $consumed > 0 ? round($covered / $consumed, 2) : null, 'liters_per_100km' => $covered > 0 ? round($consumed / $covered * 100, 2) : null, 'method' => 'full-to-full', 'note' => $consumed > 0 ? 'Pengisian penuh pertama hingga terakhir; pengisian awal tidak dihitung.' : 'Butuh minimal dua pengisian penuh pada odometer berbeda.'];
    }

    public function call(Request $r, int $id): array
    {
        $m = self::member($r, $id);
        $t = DB::table('tours')->find($id);
        abort_unless($t->mode === 'group' && $t->status !== 'finished' && ! $m->finished_at, 409);
        abort_unless(config('trieps.livekit_secret') && config('trieps.livekit_key') && config('trieps.livekit_url'), 503, 'Group call belum dikonfigurasi.');
        $enc = fn ($v) => rtrim(strtr(base64_encode(json_encode($v)), '+/', '-_'), '=');
        $h = $enc(['alg' => 'HS256', 'typ' => 'JWT']);
        $p = $enc(['iss' => config('trieps.livekit_key'), 'sub' => (string) $r->user()->id, 'name' => $r->user()->name, 'nbf' => time() - 10, 'exp' => time() + 3600, 'video' => ['roomJoin' => true, 'room' => 'trieps-tour-'.$id, 'canPublish' => true, 'canSubscribe' => true, 'canPublishData' => false, 'canPublishSources' => ['microphone']]]);
        $sig = rtrim(strtr(base64_encode(hash_hmac('sha256', "$h.$p", config('trieps.livekit_secret'), true)), '+/', '-_'), '=');

        return ['url' => config('trieps.livekit_url'), 'token' => "$h.$p.$sig"];
    }
}
