<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Pagination\LengthAwarePaginator;
use Illuminate\Support\Facades\DB;

class FeedController extends Controller
{
    public function index(Request $r): LengthAwarePaginator
    {
        $posts = DB::table('posts')->join('users', 'users.id', '=', 'posts.user_id')->select('posts.*', 'users.name')->orderByDesc('posts.id')->paginate(20);
        $posts->getCollection()->transform(function ($p) use ($r) {
            $p->photos = json_decode($p->photos ?? '[]');
            $p->route_card = json_decode($p->route_card ?? 'null');
            $p->likes = DB::table('post_likes')->where('post_id', $p->id)->count();
            $p->liked = DB::table('post_likes')->where(['post_id' => $p->id, 'user_id' => $r->user()->id])->exists();

            return $p;
        });

        return $posts;
    }

    public function upload(Request $r): array
    {
        $r->validate(['photo' => 'required|image|mimes:jpg,jpeg,png,webp|max:8192']);
        $path = $r->file('photo')->store('photos/'.$r->user()->id, 'public');

        return ['path' => $path, 'url' => url('/storage/'.$path)];
    }

    public function create(Request $r): JsonResponse
    {
        $d = $r->validate(['caption' => 'required|string|max:3000', 'photos' => 'nullable|array|max:10', 'photos.*' => 'string|max:500', 'tour_id' => 'nullable|integer', 'route_card' => 'nullable|array', 'route_card.background' => 'required_with:route_card|in:forest,night,sand', 'route_card.show_distance' => 'required_with:route_card|boolean', 'route_card.show_fuel' => 'required_with:route_card|boolean']);
        foreach ($d['photos'] ?? [] as $p) {
            abort_unless(str_starts_with($p, 'photos/'.$r->user()->id.'/') && ! str_contains($p, '..'), 422);
        }$card = null;
        if (! empty($d['tour_id'])) {
            $m = TourController::member($r, $d['tour_id']);
            abort_unless($m->finished_at, 422, 'Selesaikan touring sebelum berbagi.');
            if (isset($d['route_card'])) {
                $points = DB::table('track_points')->where(['tour_id' => $d['tour_id'], 'user_id' => $r->user()->id])->orderBy('recorded_at')->get(['latitude', 'longitude']); /* Only normalized silhouette is public; raw coordinates remain member-only. */
                $minLat = (float) ($points->min('latitude') ?? 0);
                $minLon = (float) ($points->min('longitude') ?? 0);
                $span = max((float) ($points->max('latitude') ?? 0) - $minLat, (float) ($points->max('longitude') ?? 0) - $minLon, 0.00001);
                $step = max(1, (int) ceil($points->count() / 500));
                $shape = $points->filter(fn ($v, $i) => $i % $step === 0)->map(fn ($p) => [round(((float) $p->longitude - $minLon) / $span, 5), round(((float) $p->latitude - $minLat) / $span, 5)])->values();
                $stats = (new TourController)->statistics($r, $d['tour_id']);
                $card = [...$d['route_card'], 'shape' => $shape, 'distance_km' => ($d['route_card']['show_distance'] ?? false) ? $stats['distance_km'] : null, 'km_per_liter' => ($d['route_card']['show_fuel'] ?? false) ? $stats['km_per_liter'] : null];
            }
        }abort_if(isset($d['route_card']) && ! isset($d['tour_id']), 422, 'Kartu rute memerlukan touring.');
        $id = DB::table('posts')->insertGetId(['user_id' => $r->user()->id, 'tour_id' => $d['tour_id'] ?? null, 'caption' => $d['caption'], 'photos' => json_encode($d['photos'] ?? []), 'route_card' => $card ? json_encode($card) : null, 'created_at' => now(), 'updated_at' => now()]);

        return response()->json(['id' => $id], 201);
    }

    public function like(Request $r, int $id): array
    {
        abort_unless(DB::table('posts')->find($id), 404);
        DB::table('post_likes')->insertOrIgnore(['post_id' => $id, 'user_id' => $r->user()->id]);

        return ['liked' => true];
    }

    public function unlike(Request $r, int $id): array
    {
        DB::table('post_likes')->where(['post_id' => $id, 'user_id' => $r->user()->id])->delete();

        return ['liked' => false];
    }

    public function delete(Request $r, int $id): Response
    {
        $p = DB::table('posts')->where('user_id', $r->user()->id)->find($id);
        abort_unless($p, 404);
        DB::table('posts')->where('id', $id)->delete();

        return response()->noContent();
    }
}
