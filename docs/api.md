# API summary

Session-cookie auth. Mutating requests need the CSRF token header (`X-CSRF-TOKEN`; the pages' JS does this).
Errors always look like `{"timestamp","status","code","message"}`. Interactive docs: `/swagger-ui.html` (admin).

## Any signed-in user

| Method | URL | Purpose |
|---|---|---|
| GET | `/api/me` | current user |
| POST | `/api/chat/conversations` | new conversation → 201 `{id,title,updatedAt}` |
| GET | `/api/chat/conversations` | list mine |
| GET | `/api/chat/conversations/{id}/messages` | messages with sources (404 if not yours) |
| DELETE | `/api/chat/conversations/{id}` | delete (204) |
| POST | `/api/chat/conversations/{id}/ask` | `{"question": "..."}` → answer |
| POST | `/api/feedback` | `{"messageId":1,"rating":1,"comment":"..."}` → 201 |
| GET | `/api/documents` | READY documents visible to my role |
| GET | `/api/documents/{id}/file` | PDF inline (`#page=N` in the URL); 404 if not allowed |

Ask response:

```json
{
  "conversationId": "…", "messageId": 42, "answerable": true,
  "answer": "You need at least 75% attendance. [1]",
  "sources": [{"documentId":"…","documentTitle":"Academic Regulations","filename":"reg.pdf",
               "page":14,"chunkRef":"…","excerpt":"Students must…","score":0.81}]
}
```

When not answerable: `answerable:false`, the standard "couldn't find this" message, `sources: []`.
Status codes: 400 invalid input, 404 not found/not yours, 429 daily quota, 503 AI unavailable.

## Admin only (`/api/admin/**`)

| Method | URL | Purpose |
|---|---|---|
| POST | `/documents` | multipart: `file`, `title?`, `category`, `audience`, `description?` → 202 |
| GET | `/documents?q=&status=` | list/search |
| GET | `/documents/{id}` · `/documents/{id}/status` | details · status + processing log |
| PUT | `/documents/{id}` | edit title/category/audience/description |
| POST | `/documents/{id}/replace` | multipart `file` → 202 (version+1) |
| POST | `/documents/{id}/reindex` | retry/reindex → 202 |
| DELETE | `/documents/{id}` | delete file + vectors → 204 |
| GET | `/stats` · `/questions/unanswered` · `/feedback` | dashboard data |
| GET | `/users?q=` | list/search |
| POST | `/users` | create STUDENT or FACULTY |
| PATCH | `/users/{id}` | `{"role"?, "active"?}` (not on yourself) |

Page routes: `/login`, `/register`, `/chat`, `/admin`, `/admin/documents`, `/admin/users`.
