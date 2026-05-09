$token = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJwYWdlcyI6WyJBRE1JTl9EQVNIQk9BUkQiLCJBRE1JTl9VU0VSUyIsIkFETUlOX1JPTEVTIiwiQURNSU5fUEFHRVMiLCJBRE1JTl9TRVRUSU5HUyIsIkRPQ1RPUl9EQVNIQk9BUkQiLCJET0NUT1JfUEFUSUVOVFMiLCJET0NUT1JfUFJFU0NSSVBUSU9OIiwiUkVDRVBUSU9OSVNUX0RBU0hCT0FSRCIsIlJFQ0VQVElPTklTVF9QQVRJRU5UUyIsIlJFQ0VQVElPTklTVF9BUFBPSU5UTUVOVFMiLCJSRUNFUFRJT05JU1RfQklMTElORyIsIlJFQ0VQVElPTklTVF9RVUVVRSJdLCJ0ZW5hbnRLZXkiOiJndWxlZC1ob3NwaXRhbCIsInRlbmFudElkIjozLCJhdXRob3JpdGllcyI6WyJST0xFX1JFQ0VQVElPTklTVCIsIlJPTEVfRE9DVE9SIiwiUk9MRV9BRE1JTiJdLCJzdWIiOiJmdWxsYWNjZXNzQGd1bGVkaG9zcGl0YWxzLmNvbSIsImlhdCI6MTc3ODMwMzA0NywiZXhwIjoxNzc4Mzg5NDQ3fQ.JUHKdNfijFN4FWjCrPPyhY8Mw-5Ba5t4_-Aq4Fj_4q9LBej0mVeWVyR8ds9oCSeYB0-dy5xnEy7z7JC4JH89fw"
$url = "http://localhost:8080/api/admin/service-catalog"
$headers = @{ "Authorization" = $token; "Content-Type" = "application/json" }

$services = @(
  '{"serviceName":"OPD Consultation Senior","serviceCode":"CARD-OPD-SR","price":800,"itemType":"CONSULTATION","departmentId":1,"insuranceRate":700,"gstPercentage":0,"description":"Cardiology OPD senior consultant","active":true,"isInsurancePayable":true}',
  '{"serviceName":"OPD Consultation Junior","serviceCode":"CARD-OPD-JR","price":500,"itemType":"CONSULTATION","departmentId":1,"insuranceRate":450,"gstPercentage":0,"description":"Cardiology OPD junior consultant","active":true,"isInsurancePayable":true}',
  '{"serviceName":"2D Echocardiography","serviceCode":"CARD-ECHO-01","price":2500,"itemType":"RADIOLOGY","departmentId":1,"insuranceRate":1800,"gstPercentage":5,"description":"2D Echo with color Doppler","active":true,"isInsurancePayable":true}',
  '{"serviceName":"ECG 12 Lead","serviceCode":"CARD-ECG-01","price":300,"itemType":"LAB","departmentId":1,"insuranceRate":250,"gstPercentage":0,"description":"Standard 12 lead electrocardiogram","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Treadmill Test TMT","serviceCode":"CARD-TMT-01","price":1800,"itemType":"RADIOLOGY","departmentId":1,"insuranceRate":1500,"gstPercentage":5,"description":"Exercise stress test","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Coronary Angiography CAG","serviceCode":"CARD-CAG-01","price":12000,"itemType":"PROCEDURE","departmentId":1,"insuranceRate":9500,"gstPercentage":5,"description":"Diagnostic coronary angiogram","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Coronary Angioplasty PTCA Single Vessel","serviceCode":"CARD-PTCA-01","price":45000,"itemType":"PROCEDURE","departmentId":1,"insuranceRate":38000,"gstPercentage":5,"description":"Balloon angioplasty with stent","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Holter Monitoring 24 Hour","serviceCode":"CARD-HOLTER-01","price":3500,"itemType":"RADIOLOGY","departmentId":1,"insuranceRate":3000,"gstPercentage":5,"description":"Continuous ambulatory ECG","active":true,"isInsurancePayable":true}',
  '{"serviceName":"General Consultation","serviceCode":"GEN-OPD-01","price":400,"itemType":"CONSULTATION","departmentId":1,"insuranceRate":350,"gstPercentage":0,"description":"General medicine OPD consultation","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Patient Registration Case Paper","serviceCode":"REG-CP-01","price":100,"itemType":"REGISTRATION","departmentId":1,"insuranceRate":100,"gstPercentage":0,"description":"New patient registration and case paper","active":true,"isInsurancePayable":false}',
  '{"serviceName":"X Ray Chest PA","serviceCode":"RAD-XRAY-01","price":500,"itemType":"RADIOLOGY","departmentId":3,"insuranceRate":400,"gstPercentage":5,"description":"Chest X Ray PA view","active":true,"isInsurancePayable":true}',
  '{"serviceName":"MRI Brain","serviceCode":"RAD-MRI-01","price":6500,"itemType":"RADIOLOGY","departmentId":2,"insuranceRate":5500,"gstPercentage":5,"description":"MRI Brain plain and contrast","active":true,"isInsurancePayable":true}',
  '{"serviceName":"Complete Blood Count CBC","serviceCode":"LAB-CBC-01","price":350,"itemType":"LAB","departmentId":1,"insuranceRate":300,"gstPercentage":0,"description":"Full blood count with differential","active":true,"isInsurancePayable":true}',
  '{"serviceName":"ICU Bed Charge Per Day","serviceCode":"IPD-ICU-01","price":5000,"itemType":"ICU_CHARGE","departmentId":1,"insuranceRate":4500,"gstPercentage":0,"description":"ICU bed charge per 24 hours","active":true,"isInsurancePayable":true}',
  '{"serviceName":"General Ward Bed Charge","serviceCode":"IPD-GEN-01","price":1500,"itemType":"BED_CHARGE","departmentId":1,"insuranceRate":1200,"gstPercentage":0,"description":"General ward bed per 24 hours","active":true,"isInsurancePayable":true}'
)

foreach ($body in $services) {
  try {
    $resp = Invoke-RestMethod -Uri $url -Method POST -Headers $headers -Body $body -ErrorAction Stop
    Write-Host "OK: $($resp.serviceName) - Rs.$($resp.price)" -ForegroundColor Green
  } catch {
    Write-Host "FAIL: $($_.Exception.Message)" -ForegroundColor Red
  }
}
Write-Host "`nDone! Added $($services.Count) services."
