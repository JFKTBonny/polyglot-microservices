<?php

namespace App\Console\Commands;

use App\Services\AnalyticsService;
use Illuminate\Console\Command;
use longlang\phpkafka\Consumer\ConsumeMessage;
use longlang\phpkafka\Consumer\Consumer;
use longlang\phpkafka\Consumer\ConsumerConfig;

class ConsumeKafkaEvents extends Command
{
    /**
     * The name and signature of the console command.
     * Run with: php artisan kafka:consume
     */
    protected $signature = 'kafka:consume';

    /**
     * The console command description.
     */
    protected $description = 'Consume Kafka events and store analytics data';

    public function __construct(
        private readonly AnalyticsService $analyticsService
    ) {
        parent::__construct();
    }

    public function handle(): void
    {
        $brokers          = config('kafka.bootstrap_servers');
        $groupId          = config('kafka.group_id');
        $topics           = config('kafka.topics');
        $bootstrapServers = explode(',', $brokers)[0]; // take first broker

        $this->info("[analytics] starting Kafka consumer...");
        $this->info("[analytics] broker: {$brokers}");
        $this->info("[analytics] group: {$groupId}");
        $this->info("[analytics] topics: " . implode(', ', $topics));

        // Wait for Kafka to be ready
        $this->info("[analytics] waiting 20s for Kafka to initialise...");
        sleep(20);

        // Subscribe to each topic separately
        // longlang/phpkafka subscribes one topic per consumer instance
        foreach ($topics as $topic) {
            $this->subscribeToTopic($topic, $brokers, $groupId);
        }
    }

    private function subscribeToTopic(
    string $topic,
    string $brokers,
    string $groupId
): void {
    $this->info("[kafka] subscribing to topic: {$topic}");

    $config = new ConsumerConfig();
    $config->setBroker($brokers);
    $config->setTopic($topic);
    $config->setGroupId($groupId);
    $config->setAutoCommit(false);
    $config->setInterval(0.1);          // poll every 100ms
    $config->setGroupRetry(5);          // retry group join 5 times
    $config->setGroupRetrySleep(1);     // wait 1s between retries
    $config->setOffsetRetry(5);         // retry offset commit 5 times
    $config->setAutoCreateTopic(true);  // create topic if not exists
    $config->setMaxBytes(10485760);     // 10MB max message size
    $config->setConnectTimeout(10);     // 10s connection timeout
    $config->setRecvTimeout(60);        // 60s receive timeout

    $consumer = new Consumer(
        $config,
        function (ConsumeMessage $message) use ($topic) {
            $this->processMessage($topic, $message);
        }
    );

    $consumer->start();
}

    private function processMessage(string $topic, ConsumeMessage $message): void
    {
        try {
            $raw     = $message->getValue();
            $payload = json_decode($raw, true);

            if (!$payload) {
                $this->warn("[kafka] failed to decode message on {$topic}");
                $message->getConsumer()->ack($message);
                return;
            }

            $this->info("[kafka] received on '{$topic}': order_id=" .
                ($payload['order_id'] ?? 'N/A'));

            // Process and store the event
            $this->analyticsService->processEvent($topic, $payload);

            // Acknowledge — commit offset after successful processing
            $message->getConsumer()->ack($message);

            $this->info("[kafka] ✓ event processed and stored");

        } catch (\Exception $e) {
            $this->error("[kafka] failed to process message: " . $e->getMessage());
            // Don't ack — message will be redelivered
        }
    }
}