# ShardedMC — High-Performance Minecraft Server

ShardedMC is a production-ready, modular Minecraft server implementation focused on **parallelism, asynchronous processing, and sharding** for extremely high performance and scalability.

## Features

- **Sharded architecture** — Region-based world partitioning with ownership locking
- **Async chunk pipeline** — Separate thread pools for I/O, generation, and lighting
- **Player-based scheduling** — Priority chunk loading based on player proximity and movement
- **Concurrent entity processing** — Spatial indexing with reduced ticking for distant entities
- **Adaptive performance** — Dynamic worker adjustment based on tick duration and queue depth
- **Built-in diagnostics** — `/shardedmc` commands for status, performance, shards, chunks, threads, and profiling
- **Plugin API** — Bukkit/Spigot-style plugin lifecycle via `ServiceLoader`
- **Benchmark suite** — Compare vanilla, async, and sharded chunk loading strategies
- **Paper-compatible config** — Standard `server.properties`, `bukkit.yml`, and `paper-global.yml`

## Build

Requires **Java 21+** and Gradle.

```bash
./gradlew build
```

Output JAR:

```text
build/libs/ShardedMC.jar
```

## Run

```bash
java -jar build/libs/ShardedMC.jar
```

On first run, default configuration files are generated in the server directory:

| File | Purpose |
|------|---------|
| `server.properties` | Standard Minecraft server settings |
| `config/shardedmc.yml` | Performance and sharding configuration |
| `config/paper-global.yml` | Paper-style global settings |
| `bukkit.yml` | Bukkit/Paper spawn and tick settings |

## Configuration

### server.properties

Standard Minecraft server properties including MOTD, port, max players, view distance, gamemode, and more.

### config/shardedmc.yml

```yaml
sharding:
  enabled: true
  worker-threads: auto
  region-size: 8

chunks:
  async-loading: true
  async-generation: true
  prefetch: true
  cache-size: 2048
  unload-delay: 300

network:
  worker-threads: auto
  compression-level: adaptive

performance:
  adaptive-threading: true
  profiling: false
  metrics: true
```

## Commands

```text
/shardedmc status       — Server status overview
/shardedmc performance  — Tick, latency, CPU, and memory metrics
/shardedmc shards       — Per-shard workload information
/shardedmc chunks       — Chunk cache and load queue stats
/shardedmc threads      — Thread pool utilization
/shardedmc profile      — Profiling results (start/stop)
```

## Benchmarks

```bash
./gradlew benchmark
```

Compares three chunk loading strategies:

1. Vanilla single-threaded
2. Conventional asynchronous
3. ShardedMC sharded pipeline

Metrics: TPS, tick percentiles, chunk load/generation latency, memory usage.

## Project Structure

```text
src/main/java/com/shardedmc/
├── ShardedMC.java              Entry point
├── bootstrap/                  Server startup and shutdown
├── config/                     server.properties + shardedmc.yml
├── sharding/                   Region shards and locking
├── chunk/                      Async loading, caching, serialization
├── generation/                 Terrain generation pipeline
├── entity/                     Entity partitioning and spatial index
├── scheduler/                  Tick loop and thread pools
├── network/                    Packet batching and async I/O
├── diagnostics/                Metrics, profiling, histograms
├── commands/                   /shardedmc command handlers
├── api/                        Plugin compatibility layer
├── memory/                     Object pooling and diagnostics
├── reliability/                Shutdown hooks and worker health
└── benchmark/                  Benchmark suite
```

## Compatibility Notes

ShardedMC provides configuration and API compatibility layers modeled after Paper/Bukkit. Full Minecraft protocol and gameplay compatibility requires additional protocol implementation work. Intentional differences are documented as the protocol layer is extended.

## Testing

```bash
./gradlew test
```

## License

See repository license for terms.
