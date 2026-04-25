<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Concerns\HasUuids;

class AnalyticsEvent extends Model
{
    use HasUuids;

    protected $table = 'analytics_events';

    // Mass assignable fields — required for create() to work
    protected $fillable = [
        'event_type',
        'order_id',
        'user_id',
        'amount',
        'status',
        'payload',
    ];

    // Type casting — Eloquent auto-converts these on read/write
    protected $casts = [
        'payload'    => 'array',   // JSON string ↔ PHP array automatically
        'amount'     => 'decimal:2',
        'created_at' => 'datetime',
        'updated_at' => 'datetime',
    ];
}