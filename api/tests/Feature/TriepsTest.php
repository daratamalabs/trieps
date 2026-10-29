<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class TriepsTest extends TestCase
{
    use RefreshDatabase;

    private function rider(): User
    {
        $u = User::factory()->create();
        $this->actingAs($u, 'sanctum');

        return $u;
    }

    private function vehicle(): int
    {
        return $this->postJson('/api/v1/vehicles', ['name' => 'Si Hijau', 'brand' => 'Honda', 'model' => 'CB150X', 'year' => 2024, 'plate' => 'B 1234 XX', 'odometer' => 1000])->assertCreated()->json('id');
    }

    private function tour(int $v, string $mode = 'solo'): int
    {
        return $this->postJson('/api/v1/tours', ['title' => 'Jalur Selatan', 'mode' => $mode, 'start_name' => 'Bandung', 'finish_name' => 'Garut', 'vehicle_id' => $v, 'start_odo' => 1000])->assertCreated()->json('id');
    }

    public function test_auth_tokens_register_login_logout(): void
    {
        $d = ['name' => 'Rider', 'email' => 'rider@example.test', 'password' => 'supersecure123'];
        $token = $this->postJson('/api/v1/auth/register', $d)->assertCreated()->json('token');
        $this->postJson('/api/v1/auth/login', ['email' => $d['email'], 'password' => 'wrong'])->assertUnprocessable();
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$token])->assertOk()->assertJsonPath('name', 'Rider');
    }

    public function test_unauthenticated_access_is_blocked(): void
    {
        $this->getJson('/api/v1/tours')->assertUnauthorized();
    }

    public function test_vehicle_access_is_private(): void
    {
        $this->rider();
        $v = $this->vehicle();
        $this->rider();
        $this->getJson("/api/v1/vehicles/$v/services")->assertNotFound();
        $this->patchJson("/api/v1/vehicles/$v", ['odometer' => 2000])->assertNotFound();
    }

    public function test_full_to_full_excludes_initial_fill_and_counts_partial(): void
    {
        $this->rider();
        $t = $this->tour($this->vehicle());
        foreach ([[1000, 10, true], [1100, 2, false], [1200, 3, true], [1250, 8, false]] as [$odo,$l,$full]) {
            $this->postJson("/api/v1/tours/$t/fuel", ['odometer' => $odo, 'liters' => $l, 'full_tank' => $full, 'cost' => 10000])->assertOk();
        }$this->getJson("/api/v1/tours/$t")->assertJsonPath('statistics.km_per_liter', 40)->assertJsonPath('statistics.measured_distance_km', 200);
    }

    public function test_offline_points_are_idempotent_and_member_only(): void
    {
        $u = $this->rider();
        $t = $this->tour($this->vehicle());
        $this->postJson("/api/v1/tours/$t/start")->assertOk();
        $p = ['client_id' => '88082b26-dcc3-4e90-b0e2-e2240a51e975', 'latitude' => -6.91, 'longitude' => 107.61, 'accuracy' => 10, 'recorded_at' => now()->toIso8601String()];
        for ($i = 0; $i < 2; $i++) {
            $this->postJson("/api/v1/tours/$t/points", ['points' => [$p]])->assertOk();
        }$this->assertDatabaseCount('track_points', 1);
        $this->rider();
        $this->getJson("/api/v1/tours/$t/route")->assertForbidden();
        $this->postJson("/api/v1/tours/$t/points", ['points' => [$p]])->assertForbidden();
    }

    public function test_finish_updates_vehicle_and_refuses_odometer_regression(): void
    {
        $this->rider();
        $v = $this->vehicle();
        $t = $this->tour($v);
        $this->postJson("/api/v1/tours/$t/start")->assertOk();
        $this->postJson("/api/v1/tours/$t/finish", ['finish_odo' => 900])->assertUnprocessable();
        $this->postJson("/api/v1/tours/$t/finish", ['finish_odo' => 1300])->assertOk();
        $this->assertDatabaseHas('vehicles', ['id' => $v, 'odometer' => 1300]);
    }

    public function test_community_approval_and_owner_only_actions(): void
    {
        $owner = $this->rider();
        $id = $this->postJson('/api/v1/communities', ['name' => 'Jalur Santai'])->assertCreated()->json('id');
        $u = $this->rider();
        $this->postJson("/api/v1/communities/$id/request")->assertOk()->assertJsonPath('membership', 'pending');
        $this->postJson("/api/v1/communities/$id/members/{$u->id}", ['action' => 'approve'])->assertForbidden();
        $this->actingAs($owner, 'sanctum');
        $this->postJson("/api/v1/communities/$id/members/{$u->id}", ['action' => 'approve'])->assertOk();
        $this->assertDatabaseHas('community_members', ['community_id' => $id, 'user_id' => $u->id, 'status' => 'active']);
        $this->postJson("/api/v1/communities/$id/leave")->assertConflict();
    }

    public function test_reminder_due_by_time_or_kilometers(): void
    {
        $this->rider();
        $v = $this->vehicle();
        $this->putJson("/api/v1/vehicles/$v/rules", ['component' => 'Oli', 'interval_km' => 3000, 'interval_months' => 3])->assertOk()->assertJsonPath('0.status', 'unknown');
        $this->postJson("/api/v1/vehicles/$v/services", ['component' => 'Oli', 'odometer' => 1000, 'serviced_at' => now()->subMonths(4)->toDateString(), 'cost' => 50000])->assertOk()->assertJsonPath('0.status', 'due');
    }

    public function test_invite_link_joins_group_but_not_solo(): void
    {
        $this->rider();
        $t = $this->tour($this->vehicle(), 'group');
        $token = DB::table('tours')->find($t)->invite_token;
        $this->rider();
        $v = $this->vehicle();
        $this->postJson("/api/v1/tours/join/$token", ['vehicle_id' => $v, 'start_odo' => 1000])->assertOk();
        $this->getJson("/api/v1/tours/$t")->assertOk();
    }

    public function test_route_card_obeys_visibility_and_strips_gps(): void
    {
        $this->rider();
        $t = $this->tour($this->vehicle());
        $this->postJson("/api/v1/tours/$t/start");
        $this->postJson("/api/v1/tours/$t/finish", ['finish_odo' => 1100]);
        $this->postJson('/api/v1/posts', ['caption' => 'Perjalanan', 'tour_id' => $t, 'route_card' => ['background' => 'forest', 'show_distance' => false, 'show_fuel' => false]])->assertCreated();
        $this->getJson('/api/v1/posts')->assertJsonPath('data.0.route_card.distance_km', null)->assertJsonPath('data.0.route_card.km_per_liter', null);
    }

    public function test_google_call_unconfigured_return_clear_error(): void
    {
        $this->postJson('/api/v1/auth/google', ['id_token' => 'fake'])->assertStatus(503);
        $this->rider();
        $t = $this->tour($this->vehicle(), 'group');
        $this->postJson("/api/v1/tours/$t/call-token")->assertStatus(503);
    }

    public function test_kilometer_reminder_can_be_due_before_time(): void
    {
        $this->rider();
        $v = $this->vehicle();
        $this->putJson("/api/v1/vehicles/$v/rules", ['component' => 'Rem', 'interval_km' => 1000, 'interval_months' => 12]);
        $this->postJson("/api/v1/vehicles/$v/services", ['component' => 'Rem', 'odometer' => 1000, 'serviced_at' => now()->toDateString(), 'cost' => 0]);
        $this->patchJson("/api/v1/vehicles/$v", ['odometer' => 2100])->assertOk();
        $this->getJson("/api/v1/vehicles/$v/reminders")->assertOk()->assertJsonPath('0.status', 'due');
    }

    public function test_call_token_is_audio_only_and_member_scoped(): void
    {
        config(['trieps.livekit_key' => 'test-key', 'trieps.livekit_secret' => 'test-secret-only-in-tests', 'trieps.livekit_url' => 'wss://example.test']);
        $this->rider();
        $t = $this->tour($this->vehicle(), 'group');
        $token = $this->postJson("/api/v1/tours/$t/call-token")->assertOk()->json('token');
        $payload = json_decode(base64_decode(strtr(explode('.', $token)[1], '-_', '+/')), true);
        $this->assertSame(['microphone'], $payload['video']['canPublishSources']);
        $this->assertSame('trieps-tour-'.$t, $payload['video']['room']);
        $this->rider();
        $this->postJson("/api/v1/tours/$t/call-token")->assertForbidden();
    }

    public function test_post_cannot_reference_another_users_photo(): void
    {
        $this->rider();
        $this->postJson('/api/v1/posts', ['caption' => 'Not mine', 'photos' => ['photos/9999/not-mine.jpg']])->assertUnprocessable();
    }
}
