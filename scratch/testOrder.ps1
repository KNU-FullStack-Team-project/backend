$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8081"

Write-Host "Registering test user..."
$registerJson = '{"email":"test3@test.com","password":"password123!","nickname":"TestUser3"}'
try {
    $regRes = Invoke-RestMethod -Uri "$baseUrl/api/users/register" -Method Post -ContentType "application/json" -Body $registerJson
} catch {}

Write-Host "Logging in..."
$loginJson = '{"email":"test3@test.com","password":"password123!"}'
$loginRes = Invoke-RestMethod -Uri "$baseUrl/api/users/login" -Method Post -ContentType "application/json" -Body $loginJson

$token = $loginRes.token

Write-Host "Creating Account for test user..."
$accountJson = '{"accountName":"TestAccount"}'
try {
    $accRes = Invoke-RestMethod -Uri "$baseUrl/api/accounts/create/normal" -Method Post -Headers @{Authorization="Bearer $token"} -ContentType "application/json" -Body $accountJson
} catch {}

$dashRes = Invoke-RestMethod -Uri "$baseUrl/api/accounts/my/dashboard?email=test3@test.com" -Method Get -Headers @{Authorization="Bearer $token"}

$accountId = 1
if ($dashRes.accountId) { $accountId = $dashRes.accountId }
elseif ($dashRes.id) { $accountId = $dashRes.id }

Write-Host "Using Account ID: $accountId"

Write-Host "Placing Market Buy Order for Fake Stock..."
$orderJson = @"
{
  "accountId": $accountId,
  "stockCode": "999999",
  "orderSide": "BUY",
  "orderType": "MARKET",
  "quantity": 1
}
"@

try {
    $orderRes = Invoke-RestMethod -Uri "$baseUrl/api/orders" -Method Post -Headers @{Authorization="Bearer $token"} -ContentType "application/json" -Body $orderJson
    Write-Host "========== ORDER RESULT =========="
    Write-Host $orderRes
} catch {
    Write-Host "========== ORDER FAILED =========="
    Write-Host $_.Exception.Response.StatusCode.value__
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host $reader.ReadToEnd()
}
