# Knowledge base

The knowledge layer answers one question: given a label produced by a recognition
provider, what is known about that pest, disease or weed?

The answer comes from a single table, `caas_diseases_pests_agridata`. This document
describes its columns, the lookup rule, and - more importantly - what the rule
cannot do.

---

## 1. Table

One row per pest, disease or weed. Column names are kept identical to the
production knowledge platform, so the JSON payload does not change between
deployments. On the way out, names are converted from `snake_case` to `camelCase`
(`hazard_symptoms` becomes `hazardSymptoms`).

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | internal row identifier |
| `guid` | string | stable record identifier used by the knowledge platform |
| `number` | string | catalogue number |
| `zh_name` | string, not null | canonical Chinese name; this is the join key |
| `en_name` | string | English name |
| `zh_alias_name` | string | comma-separated Chinese aliases |
| `introduction` | text | short description |
| `canonical_main_harm_crops` | string | crops affected, normalised |
| `main_harm_crops` | string | crops affected, as written in the source |
| `hazard_symptoms` | text | what the user sees on the plant |
| `pathogens_characteristics` | text | agent description for diseases |
| `driver` | text | causal agent, or the conditions that drive an outbreak |
| `characteristics` | text | occurrence pattern |
| `prevention_methods` | text | prevention and control measures |
| `page_url` | string | source page |
| `source_database` | string | provenance of the record |
| `image_name` | string | illustrative image, file name |
| `image_url` | string | illustrative image, URL |
| `type` | string | `0` disease, `1` insect pest, `2` weed |
| `senseagro_img_path` | string | provider-side image reference |
| `senseagro_name` | string | provider-side name, retained for reconciliation |
| `created_datetime` | timestamp | row created |
| `last_updated_datetime` | timestamp | row last changed |

`type` is a string, not an integer, and it is not derived by the application from
the diagnostic category. A weed returned for a crop category keeps `type=2`; the
category says what the user asked for, `type` says what the record is.

---

## 2. Lookup rule

Implemented in `JdbcKnowledgeService`, one statement:

```sql
SELECT * FROM caas_diseases_pests_agridata
 WHERE zh_name = ?
    OR zh_alias_name LIKE ?
 ORDER BY CASE WHEN zh_name = ? THEN 0 ELSE 1 END
 LIMIT 1
```

* Exact match on the canonical name wins.
* Otherwise containment on the alias column.
* One row is returned.

This is a name lookup. It is not fuzzy matching, not a synonym expansion, not
retrieval by symptom text and not an embedding search. The paper should describe
it that way, and this implementation is the reason: the behaviour above is the
whole mechanism.

---

## 3. What the rule cannot do

These are properties of the rule, not defects to be discovered later:

1. **One alias, several records.** An alias such as `玉米钻心虫` belongs to more
   than one record. `LIMIT 1` returns whichever row the database presents first,
   so the answer is not stable across storage engines unless the order is pinned.
   In the seed data the alias resolves to `玉米螟`; `桃蛀螟` also carries it and is
   only reachable by its canonical name.

2. **No normalisation.** Full-width and half-width characters, traditional and
   simplified forms, and whitespace differences are not reconciled. A provider
   that returns `小麦 条锈病` will miss.

3. **No synonym reasoning.** Aliases must be curated into `zh_alias_name`. A
   colloquial name that was never entered is simply unmatched.

4. **No ranking by relevance.** When two records match through containment, the
   first one wins; there is no scoring.

5. **Coverage is finite.** A correct label may have no record. This happens and is
   expected; it is reported as unmatched rather than hidden.

Consequence for evaluation: the knowledge layer can be assessed by retrieval hit
rate over a labelled sample (label in, record out). Reporting it as accuracy of
diagnosis would conflate two different steps.

---

## 4. Seed data versus production data

The repository ships 24 hand-written rows in `data.sql`, covering all ten
categories, plus two deliberate edge cases:

| Row | Purpose |
|---|---|
| `PD-KB-0020` 玉米螟 | the alias `玉米钻心虫` resolves here first |
| `PD-KB-0023` 桃蛀螟 | shares the same alias; reachable only by canonical name |
| `PD-KB-0001` 小麦条锈病 | the example used in the paper and in the smoke test |

They exist so the workflow can be followed offline. **They are not the production
knowledge base**, they are not a sample of it, and any rate measured on them is
meaningless.

The production knowledge base is held by the institution that operates the
service. It is not redistributed in this repository: the records are a licensed
institutional asset and the redistribution terms are separate from the MIT license
that covers the code. The table structure, the lookup rule and the JSON contract
are published instead, which is what a reader needs in order to understand and
re-implement the layer.

---

## 5. Reproducing the layer with your own data

1. Create the table with `src/main/resources/schema.sql`.
2. Populate `zh_name` (required), `zh_alias_name` (comma-separated) and `type`.
3. Point `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` at the database and start with
   `--spring.profiles.active=prod`. In `prod`, `schema.sql` is **not** executed,
   so an existing table is never dropped.
4. Query `GET /api/knowledge?name=稻瘟病` to check linkage without involving a
   recognition provider.

If your labels come from a different provider, keep the label format as a Chinese
name. If your provider returns codes or Latin names, either add them to
`zh_alias_name` or extend the lookup rule - and then update this document, because
the behaviour described above would no longer be true.
