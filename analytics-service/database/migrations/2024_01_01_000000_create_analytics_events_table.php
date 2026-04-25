<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('analytics_events', function (Blueprint $table) {
            $table->uuid('id')->primary()->default(DB::raw('gen_random_uuid()'));
            $table->string('event_type', 50);      // order.created, payment.processed etc
            $table->uuid('order_id')->nullable();
            $table->uuid('user_id')->nullable();
            $table->decimal('amount', 10, 2)->nullable();
            $table->string('status', 50)->nullable();
            $table->jsonb('payload');               // full raw event stored as JSON
            $table->timestampsTz();                 // created_at + updated_at with timezone
        });

        // Indexes for common query patterns
        Schema::table('analytics_events', function (Blueprint $table) {
            $table->index('event_type');
            $table->index('order_id');
            $table->index('user_id');
            $table->index('created_at');
        });

        // Aggregates table — pre-computed metrics updated on each event
        Schema::create('daily_metrics', function (Blueprint $table) {
            $table->id();
            $table->date('date');
            $table->integer('total_orders')->default(0);
            $table->integer('successful_payments')->default(0);
            $table->integer('failed_payments')->default(0);
            $table->decimal('total_revenue', 12, 2)->default(0);
            $table->integer('items_reserved')->default(0);
            $table->timestamps();
            $table->unique('date');   // one row per day
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('daily_metrics');
        Schema::dropIfExists('analytics_events');
    }
};