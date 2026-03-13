# 📈 Stock Tracker Sentinel

A full-stack stock portfolio tracker built with **Spring Boot 4**, **PostgreSQL**, and **Vanilla JS**. Tracks US and Malaysian (Bursa) stocks, fetches live prices in parallel using multithreading, and provides an analytics dashboard backed by stored procedures and custom JPA queries.

Built as a personal project to go beyond university assignments and explore real backend engineering concepts — concurrency, database-level security, and production-grade architecture patterns.

---

## Features

- **Live price fetching** — parallel HTTP requests using `@Async` + `CompletableFuture`, one thread per stock
- **Multi-currency support** — USD (US markets) and MYR (Bursa Malaysia) with live exchange rate conversion
- **Average cost basis merge** — adding a duplicate symbol merges positions using weighted average price instead of creating duplicates
- **Analytics dashboard** — top performers, underperformers, win rate, and portfolio summaries powered by `@Query` annotations and PostgreSQL stored procedures
- **Trade history** — deleted positions are archived to a history table instead of being permanently removed
- **Auto price refresh** — 15-second countdown with live progress bar
- **Currency toggle** — switch between MYR and USD display, persisted in session storage

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 20, Spring Boot 4.0.3, Maven |
| Database | PostgreSQL 17 |
| ORM | Hibernate 7 / JPA |
| Frontend | Vanilla HTML, CSS, JavaScript |
| Price APIs | Finnhub (US stocks), Yahoo Finance (Bursa .KL stocks) |
| Currency API | open.er-api.com (cached hourly) |
| Concurrency | Spring `@Async`, `CompletableFuture` |

---

## Architecture

The project follows a 4-layer MVC pattern:

```
HTTP Request → Controller → Service → Repository → Database
```

- **Controller** — handles HTTP only, no business logic
- **Service** — all business rules (merging, validation, analytics, thread safety)
- **Repository** — database queries via JPA, `@Query`, and native stored procedure calls
- **Entity** — direct mapping to PostgreSQL tables

### Notable implementation details

**Multithreading** — `FinnhubService.getLivePrice()` is `@Async`, launching one thread per stock simultaneously. `CompletableFuture.allOf().join()` waits for all threads before responding. Reduces price refresh from ~3.5s sequential to ~500ms parallel.

**Thread-safe currency cache** — `volatile` fields ensure cross-thread visibility of the cached rate. `ReentrantLock` with `tryLock()` prevents duplicate API calls when multiple threads trigger a cache refresh simultaneously.

**Stored procedure** — `get_portfolio_summary_by_currency()` runs aggregation inside PostgreSQL and returns total value, cost basis, gain/loss, and holding count per currency. Called via `@Query(nativeQuery = true)`.

**Smart API routing** — symbols ending in `.KL` route to Yahoo Finance (Bursa Malaysia), everything else routes to Finnhub.

---

## Running Locally

### Prerequisites
- Java 20+
- PostgreSQL 17
- Maven
- A free [Finnhub API key](https://finnhub.io)

### 1. Clone the repo
```bash
git clone https://github.com/Micro-Maxis/stock-tracker-sentinel.git
cd stock-tracker-sentinel
```

### 2. Create the database
```sql
CREATE DATABASE "stock-tracker";
```

### 3. Run the stored procedure (once)
Connect to your database and run:
```sql
CREATE OR REPLACE FUNCTION get_portfolio_summary_by_currency(p_currency VARCHAR)
RETURNS TABLE(total_value NUMERIC, total_cost NUMERIC, total_gain_loss NUMERIC, holding_count BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT
        SUM(current_price * quantity)::NUMERIC,
        SUM(buy_price * quantity)::NUMERIC,
        SUM((current_price - buy_price) * quantity)::NUMERIC,
        COUNT(*)
    FROM stocks
    WHERE currency = p_currency;
END;
$$ LANGUAGE plpgsql;
```

### 4. Configure application.properties
Copy the example config and fill in your values:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stock-tracker
spring.datasource.username=your_postgres_username
spring.datasource.password=your_postgres_password
finnhub.api.key=your_finnhub_api_key
```

### 5. Run
```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

---

## Known Limitations

| Limitation | Notes |
|---|---|
| No authentication | All endpoints are publicly accessible. Would add Spring Security + JWT in production. |
| `ddl-auto=update` | Hibernate auto-modifies the schema on startup. Safe for development, not for production — would replace with Flyway migrations. |
| Yahoo Finance is scraped | Uses an unofficial endpoint with a spoofed User-Agent header. Could break if Yahoo changes their API. Would replace with a licensed data provider in production. |
| No global error handler | Only `addStock` has validation. A `@ControllerAdvice` would cover all endpoints in production. |

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/stocks` | Get all portfolio positions |
| `POST` | `/api/stocks` | Add a new position (merges if symbol exists) |
| `PUT` | `/api/stocks/{id}` | Update a position |
| `DELETE` | `/api/stocks/{id}` | Remove a position (archives to history) |
| `GET` | `/api/stocks/history` | Get trade history |
| `GET` | `/api/stocks/summary` | Portfolio summary by currency |
| `GET` | `/api/stocks/analytics` | Analytics data (performers, stored procedure results) |
| `POST` | `/api/stocks/refresh-prices` | Trigger parallel price refresh |
| `GET` | `/api/stocks/search?query=` | Search stocks by name or symbol |
| `GET` | `/api/stocks/price-lookup?symbol=` | Look up live price for a symbol |

---

> Built by Brandon Chong — BSc Cybersecurity, Asia Pacific University
