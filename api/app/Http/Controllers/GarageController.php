<?php

namespace App\Http\Controllers;

use Carbon\Carbon;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

class GarageController extends Controller
{
    private function vehicle(Request $r, int $id): object
    {
        $v = DB::table('vehicles')->where('user_id', $r->user()->id)->find($id);
        abort_unless($v, 404);

        return $v;
    }

    public function index(Request $r): Collection
    {
        return DB::table('vehicles')->where('user_id', $r->user()->id)->orderByDesc('id')->get();
    }

    public function create(Request $r): JsonResponse
    {
        $d = $r->validate(['name' => 'required|string|max:80', 'brand' => 'required|string|max:80', 'model' => 'required|string|max:80', 'year' => 'required|integer|between:1950,2100', 'plate' => 'nullable|string|max:20', 'odometer' => 'required|numeric|min:0']);
        $id = DB::table('vehicles')->insertGetId([...$d, 'user_id' => $r->user()->id, 'created_at' => now(), 'updated_at' => now()]);

        return response()->json(DB::table('vehicles')->find($id), 201);
    }

    public function update(Request $r, int $id): object
    {
        $v = $this->vehicle($r, $id);
        $d = $r->validate(['name' => 'sometimes|required|string|max:80', 'plate' => 'nullable|string|max:20', 'odometer' => 'sometimes|required|numeric|min:'.$v->odometer]);
        DB::table('vehicles')->where('id', $id)->update([...$d, 'updated_at' => now()]);

        return DB::table('vehicles')->find($id);
    }

    public function rules(Request $r, int $id): Collection
    {
        $this->vehicle($r, $id);
        $d = $r->validate(['component' => 'required|string|max:80', 'interval_km' => 'required|integer|min:1|max:1000000', 'interval_months' => 'required|integer|min:1|max:120']);
        DB::table('service_rules')->updateOrInsert(['vehicle_id' => $id, 'component' => $d['component']], $d);

        return $this->reminders($r, $id);
    }

    public function records(Request $r, int $id): Collection
    {
        $this->vehicle($r, $id);

        return DB::table('service_records')->where('vehicle_id', $id)->orderByDesc('serviced_at')->orderByDesc('odometer')->get();
    }

    public function record(Request $r, int $id): Collection
    {
        $this->vehicle($r, $id);
        $d = $r->validate(['component' => 'required|string|max:80', 'odometer' => 'required|numeric|min:0', 'serviced_at' => 'required|date|before_or_equal:today', 'cost' => 'required|numeric|min:0', 'notes' => 'nullable|string|max:2000']);
        DB::transaction(function () use ($d, $id) {
            DB::table('service_records')->insert([...$d, 'vehicle_id' => $id, 'created_at' => now(), 'updated_at' => now()]);
            DB::table('vehicles')->where('id', $id)->where('odometer', '<', $d['odometer'])->update(['odometer' => $d['odometer']]);
        });

        return $this->reminders($r, $id);
    }

    public function reminders(Request $r, int $id): Collection
    {
        $v = $this->vehicle($r, $id);

        return DB::table('service_rules')->where('vehicle_id', $id)->get()->map(function ($rule) use ($v) {
            $last = DB::table('service_records')->where(['vehicle_id' => $v->id, 'component' => $rule->component])->orderByDesc('serviced_at')->orderByDesc('odometer')->first();
            if (! $last) {
                return [...(array) $rule, 'status' => 'unknown', 'message' => 'Tambahkan riwayat perawatan pertama.'];
            }$km = (float) $last->odometer + $rule->interval_km;
            $date = Carbon::parse($last->serviced_at)->addMonthsNoOverflow($rule->interval_months);
            $remaining = $km - (float) $v->odometer;
            $days = (int) now()->startOfDay()->diffInDays($date, false);

            return [...(array) $rule, 'last' => $last, 'next_odometer' => $km, 'next_date' => $date->toDateString(), 'remaining_km' => $remaining, 'remaining_days' => $days, 'status' => $remaining <= 0 || $days <= 0 ? 'due' : ($remaining <= 300 || $days <= 14 ? 'soon' : 'ok')];
        });
    }

    public function allReminders(Request $r): Collection
    {
        return $this->index($r)->map(fn ($v) => ['vehicle' => $v, 'reminders' => $this->reminders($r, $v->id)]);
    }
}
