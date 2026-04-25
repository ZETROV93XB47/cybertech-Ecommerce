# Redis outage (pod down or memory full)

## Symptoms
- `/actuator/health` reports `redis` component `DOWN`.
- Cart cache miss rate spikes to ~100% on the Grafana Cache dashboard.
- Slow responses on `/api/v1/services/cart/*` (p95 climbs from ~50ms to ~300-500ms — every read hits MySQL).
- Backend logs: `RedisConnectionFailureException`, `Could not get a resource from the pool`.
- Bucket4j rate-limit filter (Wave 7B) logs warnings: `Falling back to in-process bucket; distributed bucket unavailable`.

## Quick triage
```bash
# 1. Pod state
kubectl get pod -l app=redis

# 2. Memory and eviction stats
kubectl exec -it redis-0 -- redis-cli info memory | grep -E "used_memory_human|maxmemory_human|evicted"

# 3. Persistence health (BGSAVE)
kubectl exec -it redis-0 -- redis-cli info persistence | grep -E "rdb_last_bgsave_status|rdb_last_save_time"

# 4. Connectivity from app
kubectl exec deploy/cybertech-app -- redis-cli -h redis ping

# 5. Disk space (BGSAVE fails when full)
kubectl exec redis-0 -- df -h /data
```

## Root cause options (most likely first)

1. **maxmemory hit + eviction policy too aggressive** — `evicted_keys` counter climbing rapidly, frequent cart entries get pushed out before TTL.
2. **BGSAVE failing (disk full)** — `rdb_last_bgsave_status:err`. New writes start failing if `stop-writes-on-bgsave-error yes`.
3. **Pod down / crash loop** — OOMKilled, or PVC unmount issue.
4. **Config drift** — recent `helm upgrade` changed `maxmemory-policy` or `appendonly` settings.

## Mitigation

### Graceful degradation (always true)
- **Cart writes**: write-through to MySQL still works. CartServiceImp's `@CachePut` was removed in Wave 2 fix E2 — the cache helper now writes-through, and the canonical state lives in MySQL. Acceptable read latency hit while Redis is unavailable.
- **Distributed lock for cart concurrency** (Wave 8 BUG-160 fix): when Redis is unavailable, the lock acquisition gracefully falls back. The `UNIQUE(userId)` DB constraint in `cart` is the last-line defence; the retry-once logic in CartServiceImp handles the race window. **No data is lost or duplicated**, but throughput on concurrent cart writes per-user drops.
- **Bucket4j rate limiter** (Wave 7B): falls back to per-process in-memory buckets. Not great for multi-pod scaling but acceptable for a single-pod portfolio.

### If cause = maxmemory hit
- Check the policy:
  ```bash
  kubectl exec redis-0 -- redis-cli config get maxmemory-policy
  ```
- Recommended: `allkeys-lru` for cart cache. If currently `noeviction`, this is a bug — change online (NOT persisted across pod restarts; needs Helm values update too):
  ```bash
  kubectl exec redis-0 -- redis-cli config set maxmemory-policy allkeys-lru
  ```
- Bump `maxmemory` in the Helm chart values and `helm upgrade`.

### If cause = BGSAVE failing (disk full)
- Free space:
  ```bash
  kubectl exec -it redis-0 -- sh -c 'rm -f /data/temp-*.rdb /data/dump.rdb.bak'
  ```
- Resize PVC (see `db-failover.md` for the resize pattern).
- Force a successful save: `kubectl exec redis-0 -- redis-cli bgsave`, watch `rdb_last_bgsave_status:ok`.

### If cause = pod corrupt / PVC issue
- Replace the pod (cart cache will warm up cold; that's fine):
  ```bash
  kubectl delete pod redis-0
  ```
- If PVC corrupted: delete it (data is ephemeral cache):
  ```bash
  kubectl delete pvc redis-data-redis-0
  kubectl delete pod redis-0
  ```

## Verification
- `redis-cli ping` returns `PONG`.
- `/actuator/health` reports `redis` UP.
- Cart cache hit rate climbs back above 80% within ~5 min of normal traffic.
- Manual smoke: add an item to cart, refresh the page — second request should hit cache.
- Distributed lock smoke: open two browser tabs, add to cart concurrently, see only one row in `cart` table for the user.

## Postmortem checklist
- [ ] Duration of the outage and observed p95 latency on `/cart/*`.
- [ ] Was the rate limiter affected? Any 429 misclassifications?
- [ ] Was Redis-lock fallback triggered (cross-link Wave 8 BUG-160)?
- [ ] PVC capacity at incident time; new high-water threshold.
- [ ] Action item: alert on `redis_evicted_keys_total` rate > 10/s.
- [ ] Action item: alert on `rdb_last_bgsave_status != ok`.
