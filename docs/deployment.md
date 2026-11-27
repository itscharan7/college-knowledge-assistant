# Deployment

**Status:** guidance only. Nothing here has been deployed or tested.

## Local (supported path)

`docker compose up --build` runs `db` (pgvector/pgvector:pg16, named volume `pgdata`) and `app` (multi-stage image,
volume `uploads` for PDFs). The app waits for the db health check. Health endpoint: `/actuator/health`.

## Online hosting checklist

| Need | Notes |
|---|---|
| PostgreSQL with the **pgvector** extension | Not every managed Postgres allows `CREATE EXTENSION vector`; check before choosing. V2 runs `CREATE EXTENSION` (also `hstore`, `uuid-ossp`). |
| Persistent PDFs | Container filesystems are often ephemeral. Use a persistent volume, or add an object-storage implementation of `FileStorageService` (the rest of the app only uses keys). |
| Environment variables | Everything in `.env.example`; never bake secrets into the image. |
| HTTPS | Terminate TLS at the host/reverse proxy; set secure cookies (`server.servlet.session.cookie.secure=true`) and forward headers. |
| Migrations | Flyway runs automatically at startup. |
| API limits | Free tiers rate-limit; ingestion retries 3× then fails with a clear message (use *Retry*). Tune `app.ai.embed-batch-size` / `embed-batch-delay-ms`. |
| Logs/health | stdout logs; `/actuator/health` for the platform health check. |
| Backups | Dump the database (`pg_dump`) and back up the PDF volume/bucket. Vectors can be rebuilt with *Reindex*, but they cost API calls. |

Do not choose a paid service without checking its price; free hosting tiers change, so verify current terms yourself.
Keep the project runnable locally even if hosting is unavailable.
