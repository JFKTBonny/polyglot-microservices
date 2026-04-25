<?php

return [
    /*
    |--------------------------------------------------------------------------
    | Kafka Bootstrap Servers
    |--------------------------------------------------------------------------
    | Comma-separated list of Kafka brokers.
    | In Docker this is kafka:9092, locally it's localhost:9092
    */
    'bootstrap_servers' => env('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092'),

    /*
    |--------------------------------------------------------------------------
    | Consumer Group ID
    |--------------------------------------------------------------------------
    | Identifies this service as a consumer group.
    | All instances of analytics-service share this group ID.
    */
    'group_id' => 'analytics-service',

    /*
    |--------------------------------------------------------------------------
    | Topics to consume
    |--------------------------------------------------------------------------
    */
    'topics' => [
        'order.created',
        'payment.processed',
        'payment.failed',
        'stock.reserved',
        'stock.insufficient',
    ],
];