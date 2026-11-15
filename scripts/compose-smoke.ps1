$ErrorActionPreference = 'Stop'
$project = 'signature-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$api = 'http://127.0.0.1:8080/api/documents'
$broker = 'http://127.0.0.1:15672/api'
$apiUser = if ($env:DOCUMENT_API_USER) { $env:DOCUMENT_API_USER } else { 'demo' }
$apiPassword = if ($env:DOCUMENT_API_PASSWORD) { $env:DOCUMENT_API_PASSWORD } else { 'demo' }

function New-BasicHeaders($username, $password) {
    $bytes = [Text.Encoding]::UTF8.GetBytes("${username}:${password}")
    return @{ Authorization = 'Basic ' + [Convert]::ToBase64String($bytes) }
}

function Invoke-Compose {
    docker compose -p $project @args
    if ($LASTEXITCODE -ne 0) { throw "Compose command failed: $args" }
}

function Wait-Condition([scriptblock]$condition, [string]$failure) {
    for ($i = 0; $i -lt 30; $i++) {
        if (& $condition) { return }
        Start-Sleep -Seconds 1
    }
    throw $failure
}

$headers = New-BasicHeaders $apiUser $apiPassword
$brokerHeaders = New-BasicHeaders 'demo' 'demo'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    Invoke-Compose up --build -d --wait --wait-timeout 120
    Wait-Condition {
        try {
            Invoke-RestMethod $api -Headers $headers -TimeoutSec 2 | Out-Null
            return $true
        } catch {
            return $false
        }
    } 'Document API did not become ready'

    Invoke-Compose stop signature-service
    $body = '{"title":"Smoke test agreement"}'
    $document = Invoke-RestMethod $api -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    $url = "$api/$($document.id)"
    $key = "document:status:$($document.id)"
    if ($document.status -ne 'DRAFT') { throw 'Expected DRAFT at creation' }
    if ((Invoke-RestMethod $url -Headers $headers).status -ne 'DRAFT') { throw 'Expected DRAFT on first read' }
    if ((Invoke-Compose exec -T redis redis-cli GET $key) -ne 'DRAFT') { throw 'Expected cached DRAFT' }

    $accepted = Invoke-WebRequest "$url/sign" -Method Post -Headers $headers -UseBasicParsing
    if ($accepted.StatusCode -ne 202) { throw 'Expected 202 Accepted' }
    if ((Invoke-RestMethod $url -Headers $headers).status -ne 'PENDING_SIGNATURE') {
        throw 'Expected PENDING_SIGNATURE while worker is offline'
    }
    Wait-Condition {
        $queue = Invoke-RestMethod "$broker/queues/%2F/signature.requests" -Headers $brokerHeaders -TimeoutSec 2
        return $queue.messages_ready -eq 1
    } 'Signature request did not reach RabbitMQ'

    Invoke-Compose start signature-service
    Wait-Condition {
        (Invoke-RestMethod $url -Headers $headers -TimeoutSec 2).status -eq 'SIGNED'
    } 'Expected SIGNED after the worker resumes'
    if ((Invoke-Compose exec -T redis redis-cli GET $key) -ne 'SIGNED') { throw 'Expected cached SIGNED' }
    $ttl = [int](Invoke-Compose exec -T redis redis-cli TTL $key)
    if ($ttl -le 0 -or $ttl -gt 300) { throw 'Expected Redis TTL between 1 and 300 seconds' }

    $queueUrl = "$broker/queues/%2F/signature.results"
    $before = (Invoke-RestMethod $queueUrl -Headers $brokerHeaders).message_stats.ack
    $payload = @{ documentId = $document.id; eventId = [guid]::NewGuid() } | ConvertTo-Json -Compress
    $message = @{
        properties = @{ content_type = 'application/json'; delivery_mode = 2 }
        routing_key = 'signature.results'
        payload = $payload
        payload_encoding = 'string'
    } | ConvertTo-Json -Compress
    $publish = @{
        Method = 'Post'
        Headers = $brokerHeaders
        ContentType = 'application/json'
        Body = $message
    }
    $sent = Invoke-RestMethod "$broker/exchanges/%2F/signature/publish" @publish
    if (!$sent.routed) { throw 'Repeated result was not routed' }
    Wait-Condition {
        (Invoke-RestMethod $queueUrl -Headers $brokerHeaders -TimeoutSec 2).message_stats.ack -gt $before
    } 'Repeated result was not acknowledged'
    if ((Invoke-RestMethod $url -Headers $headers).status -ne 'SIGNED') { throw 'Expected SIGNED after repeated result' }
    Write-Output 'Signature smoke test passed'
} catch {
    docker compose -p $project logs --no-color
    throw
} finally {
    docker compose -p $project down --volumes
    Pop-Location
}
