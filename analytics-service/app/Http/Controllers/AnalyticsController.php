<?php

namespace App\Http\Controllers;

use App\Services\AnalyticsService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AnalyticsController extends Controller
{
    public function __construct(
        private readonly AnalyticsService $analyticsService
    ) {}

    // GET /health
    public function health(): JsonResponse
    {
        return response()->json([
            'status'  => 'ok',
            'service' => 'analytics-service',
        ]);
    }

    // GET /analytics/summary
    public function summary(): JsonResponse
    {
        return response()->json(
            $this->analyticsService->getSummary()
        );
    }

    // GET /analytics/daily
    public function daily(Request $request): JsonResponse
    {
        $days = (int) $request->query('days', 7);
        return response()->json(
            $this->analyticsService->getDailyMetrics($days)
        );
    }

    // GET /analytics/top-products
    public function topProducts(Request $request): JsonResponse
    {
        $limit = (int) $request->query('limit', 5);
        return response()->json(
            $this->analyticsService->getTopProducts($limit)
        );
    }

    // GET /analytics/events
    public function events(Request $request): JsonResponse
    {
        $limit     = (int) $request->query('limit', 20);
        $eventType = $request->query('type');

        return response()->json(
            $this->analyticsService->getRecentEvents($limit, $eventType)
        );
    }
}