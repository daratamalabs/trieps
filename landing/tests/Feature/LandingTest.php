<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class LandingTest extends TestCase
{
    use RefreshDatabase;

    public function test_home_and_privacy_render(): void
    {
        $this->get('/')->assertOk()->assertSee('Pulang bawa');
        $this->get('/privacy')->assertOk();
    }

    public function test_waitlist_validation_and_deduplication(): void
    {
        $this->post('/waitlist', ['email' => 'invalid'])->assertSessionHasErrors('email');
        for ($i = 0; $i < 2; $i++) {
            $this->post('/waitlist', ['email' => 'rider@example.test'])->assertRedirect();
        }$this->assertDatabaseCount('waitlist', 1);
    }

    public function test_invitation_is_validated(): void
    {
        $this->get('/invite/tour/'.str_repeat('a', 48))->assertOk()->assertSee('trieps://invite/tour/');
        $this->get('/invite/tour/bad')->assertNotFound();
    }
}
