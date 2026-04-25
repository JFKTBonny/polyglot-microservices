<?php

use App\Http\Controllers\AnalyticsController;
use Illuminate\Support\Facades\Route;

Route::get('/health', [AnalyticsController::class, 'health']);

Route::prefix('analytics')->group(function () {
    Route::get('/summary',      [AnalyticsController::class, 'summary']);
    Route::get('/daily',        [AnalyticsController::class, 'daily']);
    Route::get('/top-products', [AnalyticsController::class, 'topProducts']);
    Route::get('/events',       [AnalyticsController::class, 'events']);
});