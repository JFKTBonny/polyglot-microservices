<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Skip if already created by init container
        if (!Schema::hasTable('analytics_events')) {
            Schema::create('analytics_events', function (Blueprint $table) {
                $table->uuid('id')->primary()->default(DB::raw('gen_random_uuid()'));
                $table->string('event_type', 50);
                $table->uuid('order_id')->nullable();
                $table->uuid('user_id')->nullable();
                $table->decimal('amount', 10, 2)->nullable();
                $table->string('status', 50)->nullable();
                $table->jsonb('payload');
                $table->timestampsTz();
            });

            Schema::table('analytics_events', function (Blueprint $table) {
                $table->index('event_type');
                $table->index('order_id');
                $table->index('user_id');
                $table->index('created_at');
            });
        }

        if (!Schema::hasTable('daily_metrics')) {
            Schema::create('daily_metrics', function (Blueprint $table) {
                $table->id();
                $table->date('date');
                $table->integer('total_orders')->default(0);
                $table->integer('successful_payments')->default(0);
                $table->integer('failed_payments')->default(0);
                $table->decimal('total_revenue', 12, 2)->default(0);
                $table->integer('items_reserved')->default(0);
                $table->timestamps();
                $table->unique('date');
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('daily_metrics');
        Schema::dropIfExists('analytics_events');
    }
};