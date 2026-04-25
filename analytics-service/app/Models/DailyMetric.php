<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class DailyMetric extends Model
{
    protected $table = 'daily_metrics';

    protected $fillable = [
        'date',
        'total_orders',
        'successful_payments',
        'failed_payments',
        'total_revenue',
        'items_reserved',
    ];

    protected $casts = [
        'date'                => 'date',
        'total_revenue'       => 'decimal:2',
        'total_orders'        => 'integer',
        'successful_payments' => 'integer',
        'failed_payments'     => 'integer',
        'items_reserved'      => 'integer',
    ];
}