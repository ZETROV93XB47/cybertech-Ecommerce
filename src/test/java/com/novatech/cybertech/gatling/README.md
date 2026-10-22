# Cybertech Gatling Load Tests

Java-DSL Gatling simulations for the Cybertech Spring Boot backend. Three scenarios:

| Simulation | Purpose | Shape |
|---|---|---|
| `BrowseToOrderSimulation` | Happy-path catalog -> cart -> order at increasing arrival rate. | Open model: 50 -> 200 -> 500 RPS plateaus, 5-min ramp + 5-min steady each. |
| `FlashSaleSimulation` | Race-condition stress on `BUY_ONE_GET_ONE_FREE` cart adds. Validates the PRE-2 fix. | Closed model: 1000 concurrent VUs over 30 s. |
| `StripeWebhookFirehoseSimulation` | Webhook handler under sustained Stripe-style firehose. | Open model: 100 events/sec for 60 s. |

## Pre-flight: get a JWT

The simulations authenticate as a single user via a Bearer token acquired
out-of-band from Keycloak. Generate one against your target environment:

```bash
# Replace KEYCLOAK_URL, REALM, CLIENT_ID, USERNAME, PASSWORD with values
# matching the staging environment you're load-testing.
export AUTH_TOKEN=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  | jq -r .access_token)
```

Tokens are short-lived (default 5 min in our Keycloak realm). For multi-hour runs
either:
- Refresh the token periodically and re-launch the simulation, or
- Configure a `client_credentials`-grant service account with a longer TTL.

## Running

### Single simulation

```bash
./mvnw gatling:test -Ploadtest \
  -Dgatling.simulationClass=com.novatech.cybertech.gatling.BrowseToOrderSimulation \
  -Dgatling.baseUrl=https://staging.api.cybertech.local \
  -Dauth.token=${AUTH_TOKEN}
```

### All three sequentially

```bash
./mvnw gatling:test -Ploadtest \
  -Dgatling.baseUrl=https://staging.api.cybertech.local \
  -Dauth.token=${AUTH_TOKEN}
```

(Without `-Dgatling.simulationClass=...` the plugin runs every simulation it
finds under `src/test/java`.)

### Flash-sale variant

To target a specific BOGO-eligible product UUID:
```bash
./mvnw gatling:test -Ploadtest \
  -Dgatling.simulationClass=com.novatech.cybertech.gatling.FlashSaleSimulation \
  -Dflash.productUuid=<bogo-uuid-in-staging> \
  -Dgatling.baseUrl=https://staging.api.cybertech.local \
  -Dauth.token=${AUTH_TOKEN}
```

### Stripe-webhook variant

The default `Stripe-Signature` is a placeholder and will be rejected by the real
verification middleware (200 -> 400). Two options:
1. Disable HMAC verification in a dedicated `loadtest` Spring profile and run
   with no signature override (acceptable in isolated staging).
2. Pre-compute a valid signature against
   `src/test/resources/gatling/stripe-webhook-payload.json` + your Stripe
   endpoint secret, then:
   ```bash
   ./mvnw gatling:test -Ploadtest \
     -Dgatling.simulationClass=com.novatech.cybertech.gatling.StripeWebhookFirehoseSimulation \
     -Dstripe.signature='t=1719500000,v1=<computed-hex>' \
     -Dgatling.baseUrl=https://staging.api.cybertech.local
   ```

## Reports

HTML reports land at:
```
target/gatling/<simulation-class>-<timestamp>/index.html
```

Open `index.html` in a browser; the global stats, per-request percentile graphs,
and assertion results are at the top.

## SLOs

Every simulation asserts:
- p95 response time `< 1000 ms`
- error rate `< 1%` (5xx counts as error; 4xx for documented business rejections does not)

These are starting points. Tune once you have steady-state numbers from
production / staging.

### What to do if SLOs breach

In rough order of cheapest-fix-first:

1. **HikariCP saturation** - check `hikaricp_connections_pending` in Grafana. Bump
   `spring.datasource.hikari.maximum-pool-size` (currently default 10) and rerun.
2. **JVM heap pressure** - if `jvm_gc_pause_seconds` p95 > 100 ms or old-gen fills
   repeatedly, raise `-Xmx` (currently 1g in Helm chart `values.yaml`).
3. **Tomcat thread pool** - if `tomcat_threads_busy` plateaus at `max-threads`,
   raise `server.tomcat.threads.max` (default 200).
4. **Redis cache eviction** - if cart/wishlist endpoints slow down, check Redis
   `evicted_keys`; raise `maxmemory` or shorten TTL jitter.
5. **Horizontal scale** - bump replicas in the Helm chart. The app is
   stateless except for the rate-limit filter (Bucket4j is in-memory; revisit
   when going multi-replica - see TODO in `pom.xml` near `bucket4j-core`).
6. **Targeted profiling** - attach async-profiler to a single replica during
   a repeat run; flame-graph hot methods.

## Files

```
src/test/java/com/novatech/cybertech/gatling/
  BrowseToOrderSimulation.java
  FlashSaleSimulation.java
  StripeWebhookFirehoseSimulation.java
  README.md (this file)

src/test/resources/gatling/
  data/
    products.csv     # 10 placeholder product UUIDs (feeder)
    users.csv        # 10 placeholder user UUIDs (feeder)
  gatling.conf       # connection-pool / timeout overrides
  stripe-webhook-payload.json  # PaymentSucceeded fixture
```

## Why simulations don't run on `mvn test`

The `gatling-maven-plugin` binds simulation execution to its own `gatling:test`
mojo, NOT Surefire's `test` phase. The simulation classes live under
`src/test/java/` so they compile alongside the regular test sources, but
Surefire's default include patterns (`**/*Test.java`, `**/*Tests.java`) skip
files named `*Simulation.java`. We additionally exclude
`**/com/novatech/cybertech/gatling/**` explicitly in the surefire config to
make the intent unambiguous. So `mvn test` and `mvn verify` remain unaffected
by the load-test scaffold.

To prove this locally:
```bash
./mvnw test -DskipITs            # 1681 tests, no Gatling boot
./mvnw help:describe -Dplugin=gatling -Ploadtest   # describes the gatling:test mojo
```
