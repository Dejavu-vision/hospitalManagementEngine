# Production Deployment Fix

## Issue Identified

**Error:** `Unexpected token '<', "<!doctype "... is not valid JSON`

**Root Cause:** Nginx was proxying ALL requests (including `/api/*`) to the backend, but in production the backend wasn't serving the frontend. When the API request failed to reach the backend, nginx returned the frontend's `index.html`, which caused the JSON parsing error.

## Solution

Updated nginx configuration to:
1. **Serve frontend static files** from `/usr/share/nginx/html`
2. **Proxy only `/api/*` requests** to the Spring Boot backend
3. **Handle SPA routing** with `try_files` for React Router

## Files Changed

### 1. `nginx/nginx.conf`
- Added `root /usr/share/nginx/html;` to serve frontend
- Changed `location /` to serve static files with SPA fallback
- Added specific `location /api/` to proxy only API requests
- Added caching rules for static assets

### 2. `docker-compose.prod.yml`
- Added volume mount for frontend build: `../HospitalManagment/hospital-management/dist:/usr/share/nginx/html:ro`
- Added `networks` section to ensure nginx and app are on the same network

## Deployment Steps

### Step 1: Build Frontend

```bash
cd HospitalManagment/hospital-management

# Install dependencies (if not already done)
npm install

# Build for production
npm run build

# Verify dist folder was created
ls -la dist/
```

### Step 2: Update Production Config

Ensure `HospitalManagment/hospital-management/dist/config.js` has:

```javascript
window.__ENV__ = {
  VITE_API_BASE_URL: '',  // Empty = use relative path /api
  VITE_TIMEOUT_MS: '15000',
};
```

### Step 3: Deploy Backend + Nginx

```bash
cd hospitalManagementEngine

# Stop existing containers
docker-compose -f docker-compose.yml -f docker-compose.prod.yml down

# Pull latest images (if using registry)
docker-compose -f docker-compose.yml -f docker-compose.prod.yml pull

# Start services
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# Check logs
docker logs hsm-nginx -f
docker logs hsm-app -f
```

### Step 4: Verify Deployment

1. **Check nginx is serving frontend:**
   ```bash
   curl -I https://your-domain.com/
   # Should return 200 OK with HTML content
   ```

2. **Check API is being proxied:**
   ```bash
   curl https://your-domain.com/api/actuator/health
   # Should return JSON from backend
   ```

3. **Check queue dashboard API:**
   ```bash
   curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
        https://your-domain.com/api/queue/queue-dashboard
   # Should return JSON with queue data
   ```

4. **Open browser and check:**
   - Navigate to Queue Management page
   - Open DevTools → Network tab
   - Should see `/api/queue/queue-dashboard` returning JSON (not HTML)

## Alternative: If Frontend is Deployed Separately

If your frontend is deployed on a different server/CDN, you need a different nginx config:

```nginx
# For backend-only deployment
location / {
    # CORS headers for frontend on different domain
    add_header 'Access-Control-Allow-Origin' 'https://your-frontend-domain.com' always;
    add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
    add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type' always;
    add_header 'Access-Control-Allow-Credentials' 'true' always;

    if ($request_method = 'OPTIONS') {
        return 204;
    }

    proxy_pass http://hsm_app;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;
}
```

And update frontend `public/config.js`:
```javascript
window.__ENV__ = {
  VITE_API_BASE_URL: 'https://your-backend-domain.com/api',
  VITE_TIMEOUT_MS: '15000',
};
```

## Troubleshooting

### Issue: Still getting HTML instead of JSON

**Check nginx logs:**
```bash
docker logs hsm-nginx --tail=100
```

**Check if nginx config is loaded:**
```bash
docker exec hsm-nginx nginx -t
docker exec hsm-nginx cat /etc/nginx/nginx.conf
```

**Restart nginx:**
```bash
docker restart hsm-nginx
```

### Issue: 502 Bad Gateway

**Cause:** Backend not running or not reachable

**Check:**
```bash
# Check backend is running
docker ps | grep hsm-app

# Check backend logs
docker logs hsm-app --tail=100

# Check if backend is listening on port 8080
docker exec hsm-app netstat -tlnp | grep 8080
```

### Issue: 404 Not Found on API

**Cause:** Backend endpoint doesn't exist

**Check backend logs for the request:**
```bash
docker logs hsm-app --tail=100 | grep queue-dashboard
```

### Issue: Frontend shows old version

**Clear browser cache:**
- Hard refresh: Ctrl+Shift+R (Windows) or Cmd+Shift+R (Mac)
- Clear site data: DevTools → Application → Clear storage

**Verify nginx is serving new files:**
```bash
docker exec hsm-nginx ls -la /usr/share/nginx/html/
```

## Quick Rollback

If something goes wrong:

```bash
# Stop services
docker-compose -f docker-compose.yml -f docker-compose.prod.yml down

# Revert nginx config
git checkout nginx/nginx.conf

# Start with old config
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Testing Checklist

- [ ] Frontend loads at `https://your-domain.com/`
- [ ] Login works
- [ ] Queue Management page loads
- [ ] API calls return JSON (check Network tab)
- [ ] No CORS errors in console
- [ ] SSE connection works (Live indicator shows green)
- [ ] All other pages work correctly

## Notes

- The frontend build must be done **before** deploying
- The `dist` folder path in docker-compose must be correct relative to the docker-compose file location
- Nginx serves frontend on port 443 (HTTPS) and proxies `/api/*` to backend on port 8080
- Backend doesn't need to serve frontend anymore - it only handles API requests
