# Database audit

Captured: 2026-10-21T03:29:11.476045-03:00

App-local databases for com.nog.quotesapp on emulator-5554; source data unchanged.

Two consecutive captures, including WAL files, matched. Queries ran on a read-only local copy. No source records were changed.

## Checks

- SQLite integrity: ok.
- Foreign-key violations, orphan FIFO rows, duplicate links, blank quotes/tags: none.
- Exact and normalized duplicates (trim, Unicode normalization, case folding): none.

## Mobile review

Suitable for local Android storage; only the connected emulator was inspected, not physical devices or all supported API levels.

- quote_tags_cross_ref lacks an index starting with tagId; deleting tags can scan the full link table.
- quote_fifo has no foreign key to quotes; future quote deletion could leave orphan history rows.
- No explicit migrations or schema export directory are configured; plan a migration before changing the schema.
- Quote text and tag names are not unique; future duplicate content remains possible despite none being present now.
- The list screens currently load all rows; consider paging if the database becomes large.

## quotes_database

Schema version: 1. Journal mode: wal.

### android_metadata

| locale |
| --- |
| en_US |

### quote_fifo

Empty.

### quote_tags_cross_ref

| quoteId | tagId |
| --- | --- |
| 9 | 1 |
| 10 | 1 |

### quotes

| id | text |
| --- | --- |
| 9 | “Coca cola só vendeu 25 garrafas no primeiro ano. Nunca desista” |
| 10 | "Ao invés de pensarmos que não conseguimos fazer isso agora, é melhor pensarmos que podemos, e pensar como faríamos isso" |

### room_master_table

| id | identity_hash |
| --- | --- |
| 42 | e3493fd85dc26a709039b7ec2c37bf96 |

### sqlite_sequence

| name | seq |
| --- | --- |
| quotes | 10 |
| tags | 1 |

### tags

| id | name |
| --- | --- |
| 1 | Business |

IDs do not need to be consecutive. The quotes sequence is 10; earlier IDs may have been consumed by deleted records or previous experiments.

References: [Room](https://developer.android.com/training/data-storage/room), [foreign-key indexing](https://developer.android.com/reference/androidx/room/ForeignKey), [migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions).
