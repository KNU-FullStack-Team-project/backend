$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8081"

Write-Host "Logging in..."
$loginJson = '{"email":"ksg9228@naver.com","password":"k88460687@"}'
$loginRes = Invoke-RestMethod -Uri "$baseUrl/users/login" -Method Post -ContentType "application/json" -Body $loginJson

$token = $loginRes.token
Write-Host "Logged in. Got token"

$stockCode = "000100" # 유한양행
Write-Host "Targeting stock: $stockCode"

Write-Host "Fetching account ID..."
$dashRes = Invoke-RestMethod -Uri "$baseUrl/api/accounts/my/dashboard?email=ksg9228@naver.com" -Method Get -Headers @{Authorization="Bearer $token"}
$accountId = 502
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
    Write-Host "========== ORDER SUCCESS =========="
    Write-Host ($orderRes | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "========== ORDER FAILED =========="
    if ($_.Exception.Response) {
        Write-Host "Status Code: $($_.Exception.Response.StatusCode.value__)"
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "Error Response: $($reader.ReadToEnd())"
    } else {
        Write-Host "Error Message: $($_.Exception.Message)"
    }
}
