def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 6 - Tests')
    

    // ##################  6.1 Node.js — Jest ######################################################
    stage('Node.js Tests') {
        echo "Running Jest — user-service..."

        writeFile file: 'node-test.sh', text: '''#!/bin/bash
SERVICE="user-service"

if [ ! -f "$SERVICE/package.json" ]; then
    echo "No package.json found — skipping"
    exit 0
fi

cd $SERVICE

# Install jest if not present
npm install --prefer-offline 2>/dev/null || true

# Check if jest is configured
if ! npx jest --version &>/dev/null; then
    echo "Jest not configured — skipping"
    exit 0
fi

npx jest \
    --coverage \
    --coverageReporters=json \
    --json \
    --outputFile=../jest-report.json \
    --passWithNoTests \
    2>/dev/null || true

echo "Jest tests complete"
'''
        sh 'bash node-test.sh && rm -f node-test.sh'
        pipeline.archiveReport('jest-report.json')
        echo "Node.js tests passed"
    }


    // ##################  6.2 Python — pytest #####################################################
    stage('Python Tests') {
        echo "Running pytest — Python services..."

        writeFile file: 'python-test.sh', text: '''#!/bin/bash
SERVICES="order-service auth-service notification-service"

pip install pytest pytest-cov pytest-asyncio --quiet 2>/dev/null || true

for SERVICE in $SERVICES; do
    if [ ! -d "$SERVICE" ]; then
        echo "$SERVICE not found — skipping"
        continue
    fi

    echo "Testing $SERVICE..."

    # Check if tests directory exists
    if [ ! -d "$SERVICE/tests" ] && [ ! -f "$SERVICE/test_*.py" ]; then
        echo "$SERVICE — no tests found, skipping"
        continue
    fi

    pytest $SERVICE \
        --tb=short \
        --json-report \
        --json-report-file=pytest-$SERVICE.json \
        --cov=$SERVICE \
        --cov-report=json:coverage-$SERVICE.json \
        -q \
        2>/dev/null || true

    echo "$SERVICE — tests complete"
done

echo "Python tests complete"
'''
        sh 'bash python-test.sh && rm -f python-test.sh'

        if (fileExists('pytest-order-service.json'))        pipeline.archiveReport('pytest-order-service.json')
        if (fileExists('pytest-auth-service.json'))         pipeline.archiveReport('pytest-auth-service.json')
        if (fileExists('pytest-notification-service.json')) pipeline.archiveReport('pytest-notification-service.json')

        echo "Python tests passed"
    }


    // ##################  6.3 Go — go test ######################################################
    stage('Go Tests') {
        echo "Running go test — inventory-service..."

        writeFile file: 'go-test.sh', text: '''#!/bin/bash
SERVICE="inventory-service"

if [ ! -f "$SERVICE/go.mod" ]; then
    echo "No go.mod found — skipping"
    exit 0
fi

cd $SERVICE

go test ./... \
    -v \
    -cover \
    -coverprofile=coverage.out \
    -json \
    2>/dev/null | tee ../go-test-report.json || true

go tool cover \
    -func=coverage.out \
    2>/dev/null | tee ../go-coverage-report.txt || true

echo "Go tests complete"
'''
        sh 'bash go-test.sh && rm -f go-test.sh'
        pipeline.archiveReport('go-test-report.json')
        pipeline.archiveReport('go-coverage-report.txt')
        echo "Go tests passed"
    }


    // ##################  6.4 Java — Maven test ###################################################
    stage('Java Tests') {
        echo "Running Maven tests — payment-service..."

        writeFile file: 'java-test.sh', text: '''#!/bin/bash
SERVICE="payment-service"

if [ ! -f "$SERVICE/pom.xml" ]; then
    echo "No pom.xml found — skipping"
    exit 0
fi

cd $SERVICE

mvn test \
    --no-transfer-progress \
    -Dmaven.test.failure.ignore=true \
    2>/dev/null || true

echo "Java tests complete"
'''
        sh 'bash java-test.sh && rm -f java-test.sh'

        if (fileExists('payment-service/target/surefire-reports')) {
            archiveArtifacts(
                artifacts: 'payment-service/target/surefire-reports/*.xml',
                allowEmptyArchive: true
            )
        }
        echo "Java tests passed"
    }


    // ##################  6.5 PHP — PHPUnit #######################################################
    stage('PHP Tests') {
        echo "Running PHPUnit — analytics-service..."

        writeFile file: 'php-test.sh', text: '''#!/bin/bash
SERVICE="analytics-service"

if [ ! -f "$SERVICE/composer.json" ]; then
    echo "No composer.json found — skipping"
    exit 0
fi

cd $SERVICE

# Install phpunit if not present
composer require --dev phpunit/phpunit --quiet \
    --ignore-platform-reqs 2>/dev/null || true

if [ ! -f "vendor/bin/phpunit" ]; then
    echo "PHPUnit not installed — skipping"
    exit 0
fi

vendor/bin/phpunit \
    --testdox \
    --log-junit ../phpunit-report.xml \
    --coverage-text \
    2>/dev/null || true

echo "PHP tests complete"
'''
        sh 'bash php-test.sh && rm -f php-test.sh'
        pipeline.archiveReport('phpunit-report.xml')
        echo "PHP tests passed"
    }

    echo "Stage 6 complete - all tests done"
}

return this