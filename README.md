# Distributed API Gateway

A resilient API Gateway built from scratch using Java, Spring Boot, Redis, and Docker. The project explores distributed rate limiting, reverse proxying, observability, and graceful degradation patterns commonly used in modern backend systems.

## Features

### Distributed Rate Limiting

* Fixed Window algorithm
* Sliding Window algorithm (Redis Sorted Sets)
* Token Bucket algorithm
* Redis-backed shared state across multiple gateway instances
* Atomic operations using Redis Lua scripts

### API Gateway Functionality

* Reverse proxy request forwarding
* Route-specific rate limiting policies
* API key authentication
* Tier-based plans (Free / Pro / Enterprise)
* Strategy Pattern for pluggable rate limiting algorithms

### Observability

* Prometheus metrics collection
* Grafana dashboards
* Structured logging
* Request correlation IDs
* Latency tracking

### Resilience

* Redis health monitoring
* Automatic fallback to local in-memory limiting
* Graceful degradation during Redis outages
* Automatic recovery when Redis becomes available

### Infrastructure

* Docker Compose setup
* Redis integration
* Prometheus
* Grafana
* k6 load testing

---

## Rate Limiting Algorithms

### Fixed Window

Stores a request counter for a fixed time interval.

Pros:

* Simple implementation
* Low memory usage

Cons:

* Boundary burst problem

### Sliding Window

Stores request timestamps in Redis Sorted Sets.

Pros:

* Smooth traffic control
* More accurate limiting

Cons:

* Higher Redis and memory usage

### Token Bucket

Refills tokens over time and allows burst traffic.

Pros:

* Production-friendly
* Handles bursts naturally

Cons:

* More complex implementation

---

## Redis Atomicity with Lua

A distributed rate limiter must avoid race conditions when multiple gateway instances update shared state simultaneously.

Redis Lua scripts were used to make operations such as:

* Read token count
* Refill bucket
* Consume token

execute atomically on the Redis server.

This prevents inconsistent state under concurrent traffic.

---

## Resilience Design

When Redis becomes unavailable:

1. Health monitor detects failure
2. Gateway switches to local fallback limiter
3. Requests continue to be served
4. Redis reconnection is monitored
5. Distributed limiting is restored automatically after recovery

---

## Metrics & Monitoring

Prometheus metrics include:

* Total requests
* Allowed requests
* Blocked requests
* Authentication failures
* Requests by route
* Requests by tier
* Request latency

Grafana dashboards provide real-time traffic visualization and operational monitoring.

---

## Load Testing

Load testing was performed using k6.

Example test:

* 100 concurrent virtual users
* Sustained traffic generation
* Validation of rate limiting behavior
* p95 latency measurement

The gateway successfully enforced rate limits by returning HTTP 429 responses under excessive load while maintaining service availability.

---

## Tech Stack

Backend:

* Java 17
* Spring Boot

Distributed State:

* Redis
* Redis Lua Scripts

Observability:

* Prometheus
* Grafana
* Spring Actuator

Infrastructure:

* Docker
* Docker Compose

Testing:

* Postman
* k6

Build Tool:

* Maven

---

## Future Improvements

* Dynamic configuration storage
* Admin APIs for policy management
* Circuit breaker support
* Distributed tracing
* Service discovery
* High-availability Redis setup
* JWT/OAuth integration
