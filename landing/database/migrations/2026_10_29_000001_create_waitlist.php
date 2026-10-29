<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('waitlist', function (Blueprint $t) {
            $t->id();
            $t->string('email')->unique();
            $t->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('waitlist');
    }
};
