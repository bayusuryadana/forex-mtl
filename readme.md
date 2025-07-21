# Forex Rate Service – Notes

This service implements the logic described in the [Paidy Forex task](https://github.com/paidy/interview/blob/master/Forex.md).  
Below are the key assumptions, simplifications, and design decisions made during implementation.
---

## ✂️ Assumptions & Simplifications

- Currency requested only available in the [Currency](src/main/scala/forex/domain/Currency.scala) list
- Assumes single-node and DC deployment; cache is local and not distributed.
- Logging and metrics are omitted for brevity.
- No retries or circuit breaker on OneFrame failure — assuming network and OneFrame server is 100% availability.

---
## 🧩 Design System
At the beginning I was thinking to pre-populate, but it was impossible to fit in all the currency (118*117) in one url.
It might return HTTP 414 (URI Too Long). Even though theoretically 
- let's say one day = 1440 mins
- all cache is needed to be refreshed every 5 minutes = 1440 / 5 = 288 times per day
- worst case number of calls for all countries needed to be refreshed = 118
288 x 118 = 33,984 calls needed. It is still not sufficient, but it is way more efficient rather than each call only save 1 or 2 currency

So, both of these are helping to ensure that we are not reaching the limit to OneFrame
1. **Cache**: Implements caching to reduce redundant external calls and reuse data within 5 minutes period.
2. **Multiple currency at once**: This ensures all directional pairs are called by getting pair of `from` to `all` and `all` to `from`. 

---
## 🧠 Architecture

- Clean separation by responsibility:
    - `http`: HTTP routes and request/response handling
    - `program`: Core business logic and domain rules
    - `services`: External API calls and integrations
    - `common`: Shared utilities such as cache abstractions, HTTP client implementations, and error models

---

## 🚀 Running the apps
To run the apps we need to run the apps and OneFrame docker first.

```bash
sbt run
```

```bash
docker-compose up -d
```
Once both apps and docker up, test can be run by click play button on IntellijIdea one-by-one or by doing this command 
(other than Unit test will fail if docker is not up)


## 🧪 Test explanation
Only 2 tests added, unit test and integration (+ load test). Load test run by doing 1,000 call per second x 10 times 
(so, it will take 10 seconds to complete 10,000 call). Each call will call random `from` and `to` available in Currency.

---

