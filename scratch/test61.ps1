$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8081"

Write-Host "Logging in..."
$loginJson = '{"email":"ksg9228@naver.com","password":"k88460687@"}'
$loginRes = Invoke-RestMethod -Uri "$baseUrl/users/login" -Method Post -ContentType "application/json" -Body $loginJson

$token = $loginRes.token
Write-Host "Logged in. Got token"

Write-Host "Fetching 61st stock..."
$stocksRes = Invoke-RestMethod -Uri "$baseUrl/api/stocks?page=4&size=20" -Method Get
$stock = $stocksRes.content[0]
$stockCode = $stock.symbol
Write-Host "61st stock symbol is: $stockCode ($($stock.name))"

Write-Host "Fetching account ID..."
$dashRes = Invoke-RestMethod -Uri "$baseUrl/api/accounts/my/dashboard?email=ksg9228@naver.com" -Method Get -Headers @{Authorization="Bearer $token"}
$accountId = 1
if ($dashRes.accountId) { $accountId = $dashRes.accountId }
elseif ($dashRes.id) { $accountId = $dashRes.id }

Write-Host "Using Account ID: $accountId"

Write-Host "Placing Market Buy Order for $stockCode..."
$orderJson = @"
{
  "accountId": $accountId,
  "stockCode": "$stockCode",
  "orderSide": "BUY",
  "orderType": "MARKET",
  "quantity": 1
}
"@

try {
    $orderRes = Invoke-RestMethod -Uri "$baseUrl/api/orders" -Method Post -Headers @{Authorization="Bearer $token"} -ContentType "application/json" -Body $orderJson
    Write-Host "========== ORDER RESULT =========="
    Write-Host ($orderRes | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "========== ORDER FAILED =========="
    Write-Host $_.Exception.Response.StatusCode.value__
    $stream = $_.Exception.Response.GetResponseStream()
    if ($stream) {
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host $reader.ReadToEnd()
    } else {
        Write-Host $_.Exception.Message
    }
}
