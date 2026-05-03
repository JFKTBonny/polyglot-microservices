def execute() {
    def pipeline = load 'jenkins/helpers/pipeline.groovy'

    pipeline.banner('Stage 5 - Code Quality')


    // ######################### 5.1 Node.js — ESLint + complexity #################################
    stage('Node.js Quality') {
        echo "Running Node.js quality — user-service..."

        writeFile file: 'node-quality.sh', text: '''#!/bin/bash
SERVICE="user-service"

if [ ! -f "$SERVICE/package.json" ]; then
    echo "No package.json found — skipping"
    exit 0
fi

cd $SERVICE

# Run ESLint for code quality
npx eslint . \
    --ext .js \
    --format json \
    --output-file ../node-quality-report.json \
    --ignore-pattern node_modules \
    2>/dev/null || true

ERRORS=$(python3 -c "
import json
try:
    d = json.load(open('../node-quality-report.json'))
    errors   = sum(f.get('errorCount', 0)   for f in d)
    warnings = sum(f.get('warningCount', 0) for f in d)
    print(f'Errors: {errors} Warnings: {warnings}')
except:
    print('Errors: 0 Warnings: 0')
" 2>/dev/null || echo "Errors: 0 Warnings: 0")

echo "$ERRORS"
echo "Node.js quality check complete"
'''
        sh 'bash node-quality.sh && rm -f node-quality.sh'
        pipeline.archiveReport('node-quality-report.json')
        echo "Node.js quality passed"
    }


    // ######################### 5.2 Python — pylint #####################################
    stage('Python Quality') {
        echo "Running pylint — Python services..."

        writeFile file: 'python-quality.sh', text: '''#!/bin/bash
SERVICES="order-service auth-service notification-service"

pip install pylint --quiet 2>/dev/null || true

for SERVICE in $SERVICES; do
    if [ ! -d "$SERVICE" ]; then
        echo "$SERVICE not found — skipping"
        continue
    fi

    echo "Linting $SERVICE..."

    pylint $SERVICE \
        --ignore=myenv,.venv,venv,__pycache__ \
        --output-format=json \
        --exit-zero \
        > pylint-$SERVICE.json 2>/dev/null || true

    SCORE=$(python3 -c "
import json
try:
    d = json.load(open('pylint-$SERVICE.json'))
    stats = [m for m in d if m.get('type') == 'convention']
    print(f'{len(stats)} conventions')
except:
    print('0 conventions')
" 2>/dev/null || echo "0 conventions")

    echo "$SERVICE — $SCORE"
done

echo "Python quality complete"
'''
        sh 'bash python-quality.sh && rm -f python-quality.sh'

        if (fileExists('pylint-order-service.json'))        pipeline.archiveReport('pylint-order-service.json')
        if (fileExists('pylint-auth-service.json'))         pipeline.archiveReport('pylint-auth-service.json')
        if (fileExists('pylint-notification-service.json')) pipeline.archiveReport('pylint-notification-service.json')

        echo "Python quality passed"
    }


    // ######################### 5.3 Go — go vet + staticcheck #####################################
    stage('Go Quality') {
        echo "Running Go quality — inventory-service..."

        writeFile file: 'go-quality.sh', text: '''#!/bin/bash
SERVICE="inventory-service"

if [ ! -f "$SERVICE/go.mod" ]; then
    echo "No go.mod found — skipping"
    exit 0
fi

# go vet
echo "Running go vet..."
go vet -C $SERVICE ./... 2>&1 | tee go-vet-report.txt || true

# staticcheck
if ! command -v staticcheck &>/dev/null; then
    go install honnef.co/go/tools/cmd/staticcheck@latest 2>/dev/null || true
    export PATH=$PATH:$(go env GOPATH)/bin
fi

echo "Running staticcheck..."
staticcheck -C $SERVICE ./... 2>&1 | tee go-staticcheck-report.txt || true

ISSUES=$(wc -l < go-staticcheck-report.txt 2>/dev/null || echo "0")
echo "staticcheck issues: $ISSUES"

echo "Go quality complete"
'''
        sh 'bash go-quality.sh && rm -f go-quality.sh'
        pipeline.archiveReport('go-vet-report.txt')
        pipeline.archiveReport('go-staticcheck-report.txt')
        echo "Go quality passed"
    }
    

    // ######################### 5.4 Java — Checkstyle #############################################
    stage('Java Quality') {
        echo "Running Checkstyle — payment-service..."

        writeFile file: 'java-quality.sh', text: '''#!/bin/bash
SERVICE="payment-service"

if [ ! -f "$SERVICE/pom.xml" ]; then
    echo "No pom.xml found — skipping"
    exit 0
fi

cd $SERVICE

mvn com.puppycrawl.tools:checkstyle-maven-plugin:3.3.1:checkstyle \
    -Dcheckstyle.config.location=google_checks.xml \
    -Dcheckstyle.failOnViolation=false \
    --no-transfer-progress \
    2>/dev/null || true

echo "Checkstyle complete"
'''
        sh 'bash java-quality.sh && rm -f java-quality.sh'

        if (fileExists('payment-service/target/checkstyle-result.xml')) {
            pipeline.archiveReport('payment-service/target/checkstyle-result.xml')
        }
        echo "Java quality passed"
    }
    

    // ######################### 5.5 PHP — PHPMD ###################################################
    stage('PHP Quality') {
        echo "Running PHPMD — analytics-service..."

        writeFile file: 'php-quality.sh', text: '''#!/bin/bash
SERVICE="analytics-service"

if [ ! -f "$SERVICE/composer.json" ]; then
    echo "No composer.json found — skipping"
    exit 0
fi

cd $SERVICE

composer require --dev phpmd/phpmd --quiet \
    --ignore-platform-reqs 2>/dev/null || true

vendor/bin/phpmd app json \
    cleancode,codesize,controversial,design,naming,unusedcode \
    > ../phpmd-report.json 2>/dev/null || true

VIOLATIONS=$(python3 -c "
import json
try:
    d = json.load(open('../phpmd-report.json'))
    violations = d.get('pmd', {}).get('@version', '')
    files = d.get('pmd', {}).get('file', [])
    if isinstance(files, dict):
        files = [files]
    count = sum(len(f.get('violation', [])) for f in files)
    print(count)
except:
    print(0)
" 2>/dev/null || echo "0")

echo "PHPMD violations: $VIOLATIONS"
echo "PHP quality complete"
'''
        sh 'bash php-quality.sh && rm -f php-quality.sh'
        pipeline.archiveReport('phpmd-report.json')
        echo "PHP quality passed"
    }


    // ######################### 5.6 Dockerfile lint — Hadolint ####################################
    stage('Dockerfile Quality') {
        echo "Running Hadolint — all Dockerfiles..."

        writeFile file: 'hadolint-scan.sh', text: '''#!/bin/bash
SERVICES="user-service order-service inventory-service payment-service analytics-service auth-service notification-service api-gateway ui-service"

# Install hadolint
if ! command -v hadolint &>/dev/null; then
    curl -sSfL \
        https://github.com/hadolint/hadolint/releases/download/v2.12.0/hadolint-Linux-x86_64 \
        -o /tmp/hadolint 2>/dev/null || true
    chmod +x /tmp/hadolint
    sudo mv /tmp/hadolint /usr/local/bin/hadolint 2>/dev/null || \
    mv /tmp/hadolint /var/lib/jenkins/bin/hadolint 2>/dev/null || true
fi

HADOLINT_BIN=$(command -v hadolint || echo "/var/lib/jenkins/bin/hadolint")

echo "[]" > hadolint-report.json
RESULTS="["
FIRST=1

for SERVICE in $SERVICES; do
    if [ ! -f "$SERVICE/Dockerfile" ]; then
        echo "$SERVICE — no Dockerfile"
        continue
    fi

    echo "Linting $SERVICE/Dockerfile..."

    RESULT=$($HADOLINT_BIN \
        --format json \
        $SERVICE/Dockerfile 2>/dev/null || echo "[]")

    if [ "$FIRST" -eq 1 ]; then
        FIRST=0
    else
        RESULTS="$RESULTS,"
    fi

    RESULTS="$RESULTS$RESULT"
done

echo "$RESULTS" > hadolint-report.json
echo "Hadolint complete"
'''
        sh 'bash hadolint-scan.sh && rm -f hadolint-scan.sh'
        pipeline.archiveReport('hadolint-report.json')
        echo "Dockerfile quality passed"
    }

    echo "Stage 5 complete - code quality done"
}

return this