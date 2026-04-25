# MySQL failover (pod crashloop or PVC corruption)

## Symptoms
- `/actuator/health` reports `DOWN` with `db` component failing.
- Grafana DB dashboard: HikariCP `pool.connections.active` saturated (= maxPoolSize) and `pool.connections.pending` climbing.
- Backend logs: `Communications link failure`, `HikariPool-1 - Connection is not available, request timed out after 30000ms`.
- Cascading failures: Keycloak goes down (it shares the MySQL instance), checkout returns 500.

## Quick triage
```bash
# 1. Pod state and reason for restart
kubectl describe pod mysql-0 | tail -40

# 2. Recent logs
kubectl logs mysql-0 --tail=200

# 3. PVC bound + capacity
kubectl get pvc | grep mysql
kubectl exec mysql-0 -- df -h /var/lib/mysql

# 4. Try to connect
kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "SELECT VERSION(); SHOW DATABASES; SHOW PROCESSLIST;" | head -50

# 5. Connection pool from app side
kubectl exec deploy/cybertech-app -- curl -s localhost:8080/actuator/metrics/hikaricp.connections.active
```

## Root cause options (most likely first)

1. **PVC full** — `df -h` shows `/var/lib/mysql` at 100%. Common after long-running test/seeding loops or unbounded log tables.
2. **Connection pool saturated** — symptom of upstream slow query, not MySQL itself. App holding connections too long.
3. **Disk corruption** — InnoDB refuses to start, error log shows `Cannot find table` or `Page is corrupted`.
4. **Concurrent restart / config change** — recent `kubectl apply` on the StatefulSet, possibly related to an in-progress backup-restore.

## Mitigation

### If cause = PVC full
- Free space immediately by purging old binlogs and `userEvent` MongoDB-equivalent log tables (NOTE: userEvent lives in MongoDB, not MySQL — see `gdpr-data-handling.md`).
  ```bash
  kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "PURGE BINARY LOGS BEFORE NOW() - INTERVAL 7 DAY;"
  ```
- Resize the PVC (online resize — must be supported by the storage class):
  ```bash
  kubectl edit pvc mysql-data-mysql-0  # bump spec.resources.requests.storage
  kubectl rollout restart statefulset/mysql
  ```

### If cause = pool saturated
- Drain dependents to release connections:
  ```bash
  kubectl scale deploy/cybertech-app --replicas=0
  # wait 30s, then bring back gradually
  kubectl scale deploy/cybertech-app --replicas=1
  ```
- Find the offending query: `SHOW PROCESSLIST;` -> kill long-running ones with `KILL <id>`.
- Tune `spring.datasource.hikari.maximum-pool-size` if consistently saturated.

### If cause = disk corruption
- **Portfolio-scope decision**: data is expendable; restart with a fresh PVC.
  ```bash
  kubectl delete pvc mysql-data-mysql-0
  kubectl delete pod mysql-0  # StatefulSet recreates PVC + pod
  ```
- The mysql init script (Wave 8A1) re-creates the `cybertech` and `keycloakDB` schemas on first boot. Seed data must be re-run (see Helm chart README at `src/main/resources/k8s/helm/README.md`).
- If data is NOT expendable: restore from snapshot (see "Real prod note" below).

### If cause = config drift
- `helm rollback` the MySQL chart (see `release-rollback.md`).

## Real prod note (NOT applicable to portfolio)
In a real production environment, this runbook would cover:
- **RDS multi-AZ failover** — automatic, ~30-90s RTO.
- **Point-in-time recovery** from binlog backups — RPO ~5 min.
- **Read replica promotion** — manual cutover after split-brain check.

On minikube there are no automated backups. **Accepted risk for portfolio**: data loss after a corruption event. See "Backups" section in the Operational Notes / future-work backlog. Seed data is deterministic; integration test fixtures can rebuild a usable dataset.

## Verification
- `kubectl get pod mysql-0` shows `Running` with no recent restarts.
- `/actuator/health` reports `UP` for the `db` component.
- HikariCP active connections drop back to baseline (~5-10 idle).
- Sample read + write smoke:
  ```sql
  SELECT COUNT(*) FROM productTable;
  INSERT INTO healthcheck (ts) VALUES (NOW()); -- if a healthcheck table exists
  ```
- Keycloak's pod transitions back to Ready (it depends on this DB).

## Postmortem checklist
- [ ] Was data lost? If yes, scope of loss + what was reseedable.
- [ ] PVC capacity at the time of incident; new high-watermark threshold.
- [ ] Did Keycloak cascade? Cross-link `keycloak-outage.md` postmortem.
- [ ] Action item: implement scheduled `mysqldump` to S3/localstack as a safety net (currently in backlog).
- [ ] Action item: alert on PVC > 80% full.
