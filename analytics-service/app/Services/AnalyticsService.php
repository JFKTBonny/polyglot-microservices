<?php

namespace App\Services;

use App\Models\AnalyticsEvent;
use App\Models\DailyMetric;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

class AnalyticsService
{
    /**
     * Process an incoming Kafka event and store it.
     * Called by the Kafka consumer for every message received.
     */
    public function processEvent(string $eventType, array $payload): void
    {
        DB::transaction(function () use ($eventType, $payload) {

            // 1 — Store the raw event
            $event = AnalyticsEvent::create([
                'event_type' => $eventType,
                'order_id'   => $payload['order_id'] ?? null,
                'user_id'    => $payload['user_id']  ?? null,
                'amount'     => $payload['total']    ?? $payload['amount'] ?? null,
                'status'     => $payload['status']   ?? null,
                'payload'    => $payload,
            ]);

            Log::info("[analytics] stored event", [
                'type'     => $eventType,
                'order_id' => $event->order_id,
            ]);

            // 2 — Update daily metrics
            $this->updateDailyMetrics($eventType, $payload);

        });
    }

    /**
     * Update pre-aggregated daily metrics table.
     */
    private function updateDailyMetrics(string $eventType, array $payload): void
    {
        $today = now()->toDateString();

        $metrics = DailyMetric::firstOrCreate(
            ['date' => $today],
            [
                'total_orders'        => 0,
                'successful_payments' => 0,
                'failed_payments'     => 0,
                'total_revenue'       => 0,
                'items_reserved'      => 0,
            ]
        );

        if ($eventType === 'order.created') {
            $metrics->increment('total_orders');

        } elseif ($eventType === 'payment.processed') {
            $status = $payload['status'] ?? '';
            if ($status === 'completed') {
                $metrics->increment('successful_payments');
                $amount = (float) ($payload['amount'] ?? $payload['total'] ?? 0);
                $metrics->increment('total_revenue', $amount);
            }

        } elseif ($eventType === 'payment.failed') {
            $metrics->increment('failed_payments');

        } elseif ($eventType === 'stock.reserved') {
            $items      = $payload['items'] ?? [];
            $totalItems = array_sum(array_column($items, 'quantity'));
            if ($totalItems > 0) {
                $metrics->increment('items_reserved', $totalItems);
            }
        }

        Log::debug("[analytics] daily metrics updated for {$today}");
    }

    /**
     * Get overall summary statistics.
     */
    public function getSummary(): array
    {
        $totalOrders    = AnalyticsEvent::where('event_type', 'order.created')->count();
        $totalRevenue   = AnalyticsEvent::where('event_type', 'payment.processed')
                            ->where('status', 'completed')
                            ->sum('amount');
        $totalPayments  = AnalyticsEvent::where('event_type', 'payment.processed')->count();
        $failedPayments = AnalyticsEvent::where('event_type', 'payment.failed')->count();

        return [
            'total_orders'         => $totalOrders,
            'total_revenue'        => round((float) $totalRevenue, 2),
            'total_payments'       => $totalPayments,
            'failed_payments'      => $failedPayments,
            'payment_success_rate' => $totalPayments > 0
                ? round((($totalPayments - $failedPayments) / $totalPayments) * 100, 1)
                : 0,
        ];
    }

    /**
     * Get daily metrics for the last N days.
     */
    public function getDailyMetrics(int $days = 7): array
    {
        return DailyMetric::orderBy('date', 'desc')
            ->limit($days)
            ->get()
            ->toArray();
    }

    /**
     * Get top products by order frequency.
     */
    public function getTopProducts(int $limit = 5): array
    {
        return DB::select("
            SELECT
                item->>'product_id' as product_id,
                item->>'name'       as name,
                SUM((item->>'quantity')::int) as total_ordered
            FROM analytics_events,
                 jsonb_array_elements(payload->'items') as item
            WHERE event_type = 'order.created'
            GROUP BY item->>'product_id', item->>'name'
            ORDER BY total_ordered DESC
            LIMIT ?
        ", [$limit]);
    }

    /**
     * Get recent events with optional filter by type.
     */
    public function getRecentEvents(int $limit = 20, ?string $eventType = null): array
    {
        $query = AnalyticsEvent::orderBy('created_at', 'desc')
            ->limit($limit);

        if ($eventType) {
            $query->where('event_type', $eventType);
        }

        return $query->get()->toArray();
    }
}