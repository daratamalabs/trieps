<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $t) {
            $t->string('google_id')->nullable()->unique();
        });
        Schema::create('vehicles', function (Blueprint $t) {
            $t->id();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->string('name');
            $t->string('brand');
            $t->string('model');
            $t->unsignedInteger('year');
            $t->string('plate')->nullable();
            $t->decimal('odometer', 12, 1)->default(0);
            $t->timestamps();
        });
        Schema::create('service_rules', function (Blueprint $t) {
            $t->id();
            $t->foreignId('vehicle_id')->constrained()->cascadeOnDelete();
            $t->string('component');
            $t->unsignedInteger('interval_km');
            $t->unsignedInteger('interval_months');
            $t->unique(['vehicle_id', 'component']);
        });
        Schema::create('service_records', function (Blueprint $t) {
            $t->id();
            $t->foreignId('vehicle_id')->constrained()->cascadeOnDelete();
            $t->string('component');
            $t->decimal('odometer', 12, 1);
            $t->date('serviced_at');
            $t->decimal('cost', 14, 2)->default(0);
            $t->text('notes')->nullable();
            $t->timestamps();
        });
        Schema::create('tours', function (Blueprint $t) {
            $t->id();
            $t->foreignId('owner_id')->constrained('users')->cascadeOnDelete();
            $t->string('title');
            $t->string('mode');
            $t->string('status')->default('planned');
            $t->string('start_name');
            $t->string('finish_name');
            $t->string('invite_token', 64)->unique();
            $t->timestamps();
        });
        Schema::create('tour_members', function (Blueprint $t) {
            $t->id();
            $t->foreignId('tour_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->foreignId('vehicle_id')->constrained();
            $t->decimal('start_odo', 12, 1);
            $t->decimal('finish_odo', 12, 1)->nullable();
            $t->timestamp('started_at')->nullable();
            $t->timestamp('finished_at')->nullable();
            $t->unique(['tour_id', 'user_id']);
        });
        Schema::create('checkpoints', function (Blueprint $t) {
            $t->id();
            $t->foreignId('tour_id')->constrained()->cascadeOnDelete();
            $t->string('name');
            $t->decimal('latitude', 10, 7)->nullable();
            $t->decimal('longitude', 10, 7)->nullable();
            $t->unsignedInteger('position')->default(0);
        });
        Schema::create('checkpoint_visits', function (Blueprint $t) {
            $t->id();
            $t->foreignId('checkpoint_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->decimal('odometer', 12, 1);
            $t->timestamp('visited_at');
            $t->unique(['checkpoint_id', 'user_id']);
        });
        Schema::create('track_points', function (Blueprint $t) {
            $t->id();
            $t->foreignId('tour_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->uuid('client_id');
            $t->decimal('latitude', 10, 7);
            $t->decimal('longitude', 10, 7);
            $t->float('accuracy');
            $t->timestamp('recorded_at');
            $t->unique(['user_id', 'client_id']);
            $t->index(['tour_id', 'user_id', 'recorded_at']);
        });
        Schema::create('fuel_entries', function (Blueprint $t) {
            $t->id();
            $t->foreignId('tour_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->decimal('odometer', 12, 1);
            $t->decimal('liters', 9, 3);
            $t->decimal('cost', 14, 2)->default(0);
            $t->boolean('full_tank')->default(false);
            $t->timestamps();
        });
        Schema::create('communities', function (Blueprint $t) {
            $t->id();
            $t->foreignId('owner_id')->constrained('users')->cascadeOnDelete();
            $t->string('name');
            $t->text('description')->nullable();
            $t->string('invite_token', 64)->unique();
            $t->timestamps();
        });
        Schema::create('community_members', function (Blueprint $t) {
            $t->id();
            $t->foreignId('community_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->string('status');
            $t->unique(['community_id', 'user_id']);
        });
        Schema::create('posts', function (Blueprint $t) {
            $t->id();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->foreignId('tour_id')->nullable()->constrained()->nullOnDelete();
            $t->text('caption');
            $t->json('photos')->nullable();
            $t->json('route_card')->nullable();
            $t->timestamps();
        });
        Schema::create('post_likes', function (Blueprint $t) {
            $t->id();
            $t->foreignId('post_id')->constrained()->cascadeOnDelete();
            $t->foreignId('user_id')->constrained()->cascadeOnDelete();
            $t->unique(['post_id', 'user_id']);
        });
        Schema::create('waitlist', function (Blueprint $t) {
            $t->id();
            $t->string('email')->unique();
            $t->timestamps();
        });
    }

    public function down(): void
    {
        foreach (['waitlist', 'post_likes', 'posts', 'community_members', 'communities', 'fuel_entries', 'track_points', 'checkpoint_visits', 'checkpoints', 'tour_members', 'tours', 'service_records', 'service_rules', 'vehicles'] as $name) {
            Schema::dropIfExists($name);
        }Schema::table('users', fn (Blueprint $t) => $t->dropColumn('google_id'));
    }
};
