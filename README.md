# PlantDoctor

Knowledge-enhanced diagnostic tool for crop pests, diseases and weeds.

This repository contains the reference implementation of the diagnostic workflow
described in the paper: a user selects one of ten diagnostic categories, uploads
an image, the request is routed to the matching recognition contract, the returned
label is linked to a knowledge record, and the whole transaction is written to an
access log.

The implementation is deliberately small. It reproduces the workflow, the routing
table and the JSON contracts of the deployed system, and it runs with no external
service and no credential. It is not a copy of the platform the production service
is embedded in; see [Relationship to the production deployment](#8-relationship-to-the-production-deployment).

Repository: <https://github.com/yanfangwang05/plantdoctor> · License: MIT

---

## 1. Quick start

Requirements: JDK 17 and Maven 3.9 or newer. Nothing else. No database, no
registry, no message broker, no image-recognition account.

```bash
git clone https://github.com/yanfangwang05/plantdoctor.git
cd plantdoctor
mvn clean package
java -jar target/plantdoctor-1.0.0.jar
```

Then open <http://localhost:8080>. The web page lists the 8 categories, lets you
upload an image, and shows the label, the linked knowledge record and the recent
request log.

Expected startup output, in the offline profile:

```
The following 1 profile is active: "mock"
Tomcat started on port 8080 (http)
Started PlantDoctorApplication in 2.1 seconds
Mock recognition fixtures loaded: 0 by hash, 13 by file name, 8 category defaults
```

Health check:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Command line diagnosis, using one of the bundled sample images:

```bash
curl -F "cropType=WHEAT" -F "file=@samples/wheat_rust.jpg" \
     http://localhost:8080/api/diagnosis
```

Full verification (build, start, 25 assertions, shutdown):

```bash
./scripts/smoke-test.sh
```

---

## 2. Repository layout

```
pom.xml                     Spring Boot 3.3.5, JDK 17, Maven Central artifacts only
src/main/java/cn/caas/plantdoctor/
  domain/InsectCropType     the routing table of Table 1, as an enumeration
  controller/               the three HTTP endpoints
  service/RecognitionService      provider boundary (mock | remote)
  service/KnowledgeService        knowledge lookup boundary
  repository/               access log persistence
  util/FileStorageUtil      upload validation and renaming
src/main/resources/
  application.yml           base configuration, default profile "mock"
  application-mock.yml      offline profile
  application-prod.yml      real database and real recognition services
  schema.sql, data.sql      in-memory schema and 24 seed knowledge records
  mock/recognition.json     recognition fixtures (not a model, see section 6)
  static/index.html         the demo page, no external assets
docs/                       API contracts, data dictionary, desensitization record
samples/                    small images that map to fixtures
scripts/smoke-test.sh       end-to-end verification
```

---

## 3. What the workflow does

```
image + category
      |
      v
[ routing ]  category -> provider identifier (Table 1)
      |
      +--> general path   (8 categories)  POST crop_id + image URL
      +--> dedicated path (corn, soybean) multipart imageType + bytes
      |
      v
[ label + confidence ]
      |
      v
[ knowledge linkage ]  zh_name exact match, then zh_alias_name containment
      |
      v
[ access log ]  label, confidence, provider, matched flag, raw response
```

### 3.1 Routing table

Ten categories are selectable by the user: seven crops (apple, citrus, grape,
rice, wheat, soybean, corn) and three damage types (insect pest, fall armyworm,
grass weed). Corn and soybean have no coverage on the general endpoint and are
served by a separate contract, hence the two paths.

The enumeration is the single source of truth; the same table can be read at
runtime from `GET /api/crop-types`, so the table in the paper can be checked
against the code without reading Java sources.

| Category | Chinese | Path | Provider identifier |
|---|---|---|---|
| APPLE | 苹果 | general | `crop_id=123` |
| CITRUS | 柑橘 | general | `crop_id=1` |
| GRAPE | 葡萄 | general | `crop_id=127` |
| RICE | 水稻 | general | `crop_id=132` |
| WHEAT | 小麦 | general | `crop_id=131` |
| INSECT | 虫体 | general | `crop_id=143` |
| YEE | 草地贪夜蛾 | general | `crop_id=142` |
| GRASS | 杂草 | general | `crop_id=2` |

### 3.2 Recognition contracts

Both contracts belong to a commercial provider and are documented, without
credentials, in [docs/AI_SERVICE.md](docs/AI_SERVICE.md). The general path first
exchanges a client identifier for an access token (cached for 23 hours, one hour
short of its stated lifetime) and then submits an image reference. The dedicated
path submits the image bytes as multipart form data together with a license
string issued to the institution.

A provider returns a label (a Chinese pest, disease or weed name) and a
confidence score. It does not return an identifier, a taxonomy code or a
knowledge record; the linkage step exists precisely because of that.

### 3.3 Knowledge linkage

Linkage is a name-based lookup, not a semantic match:

1. exact match on the canonical Chinese name;
2. otherwise containment on the alias column.

One alias may belong to several records; the current implementation returns the
first match and records that only one record was returned. This is stated plainly
because it bounds what the knowledge layer can claim: it is a lookup, and calling
it retrieval or reasoning would misrepresent the system. Measured behaviour is in
[docs/KNOWLEDGE_BASE.md](docs/KNOWLEDGE_BASE.md).

Requests whose label finds no record are **not** reported as a diagnosis. They are
answered with the label, no knowledge payload, and `knowledgeMatched=false` in the
log, so the share of unmatched labels can be reported separately from the share of
successful diagnoses.

---

## 4. HTTP API

Every endpoint answers with the same envelope:

```json
{"success": true, "message": null, "data": {...}}
```

| Method | Path | Parameters | Purpose |
|---|---|---|---|
| GET | `/api/crop-types` | – | routing table, ten entries |
| GET | `/api/meta` | – | version, active recognition mode, category count |
| POST | `/api/diagnosis` | `file`, `cropType` | the full workflow |
| GET | `/api/knowledge` | `name` | knowledge lookup without recognition |
| GET | `/api/diagnosis/logs` | `limit` (default 20, max 200) | recent requests |
| GET | `/actuator/health` | – | liveness, used by the smoke test |

`cropType` accepts the numeric code, the upper-case key (`WHEAT`) or the display
name (`Wheat`). An unknown category is rejected before any file is stored.

Diagnosis response:

```json
{
  "success": true,
  "data": {
    "diagnosis": {"insectName": "小麦条锈病", "trustLevel": 0.9899, "provider": "mock"},
    "diseaseData": {"zhName": "小麦条锈病", "hazardSymptoms": "...", "preventionMethods": "..."},
    "logId": "9f1c..."
  }
}
```

`diseaseData` is empty when the label has no knowledge record. Keys are camel
case, converted from the production column names, so the payload shape is the same
in both deployments.

---

## 5. Configuration

### 5.1 Profiles

| Profile | Database | Recognition | Intended use |
|---|---|---|---|
| `mock` (default) | embedded H2, created at start | bundled fixtures | reviewers, demonstrations, tests |
| `prod` | MySQL, schema untouched | real provider | the deployed service |

```bash
java -jar target/plantdoctor-1.0.0.jar                              # mock
java -jar target/plantdoctor-1.0.0.jar --spring.profiles.active=prod # prod
```

`prod` sets `spring.sql.init.mode=never` on purpose: the production database
already holds the knowledge records, and running `schema.sql` would drop the table.

The profile decides the recognition provider; there is no second switch. In `prod`
a missing credential fails the request with an explicit message instead of falling
back to fixtures, so a fixture label can never be mistaken for a production result.

### 5.2 Environment variables

Copy `.env.example` to `.env` and fill in only what you have. Every variable is
optional for the default profile.

| Variable | Required when | Meaning |
|---|---|---|
| `RECOGNITION_BASE_URL`, `RECOGNITION_TOKEN_PATH`, `RECOGNITION_PREDICT_PATH` | mode `remote`, general path | endpoint addresses |
| `RECOGNITION_CLIENT_ID`, `RECOGNITION_CLIENT_SECRET` | mode `remote`, general path | credentials issued by the vendor |
| `CORN_SOY_URL`, `CORN_SOY_LICENSE` | mode `remote`, corn or soybean | dedicated endpoint and license |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | profile `prod` | MySQL connection |
| `PLANTDOCTOR_UPLOAD_DIR` | optional | where uploads are written, default `./uploads` |
| `PLANTDOCTOR_PUBLIC_BASE_URL` | mode `remote`, general path | public URL of `/uploads/**`, because that contract fetches the image by URL |

Credentials are never stored in the repository, in `application*.yml`, or in the
test fixtures. Values are read from the environment at start-up.

---

## 6. Mock mode: what it is and what it is not

Mock mode exists so that a reader can reproduce the workflow without a commercial
agreement. It replaces exactly one component, the recognition provider. Routing,
validation, knowledge linkage, logging and the HTTP contracts are the real code.

**The labels in `mock/recognition.json` are fixed answers, not predictions.**

- They are selected by image hash, then by file name, then by category default.
- They are not produced by a model and do not respond to image content.
- Any accuracy, precision or recall figure computed in this mode is meaningless.
- Accuracy figures in the paper were obtained with the real providers in `prod`.

In a publication or derivative work, state the mode used. Running the demo is not
an evaluation.

---

## 7. Tests

```bash
mvn test                 # 15 tests
./scripts/smoke-test.sh  # builds, starts, 25 assertions, stops
```

`mvn test` covers the routing table, the end-to-end workflow, alias linkage, the
unmatched-label path, input validation and log persistence.

`scripts/smoke-test.sh` does the same over real HTTP against the packaged jar. It
verifies that the artefact starts with no external dependency, then exercises:

| Check | Why it matters |
|---|---|
| `/actuator/health` responds | the artefact starts with no database and no credential |
| ten categories are served | the published routing table matches the code |
| WHEAT and CORN diagnose end to end | both contracts and both paths are wired |
| an alias resolves to a canonical record | linkage falls back to the alias column |
| an unknown label is reported unmatched | a label without knowledge is not presented as a diagnosis |
| an unknown category and a non-image are rejected | input validation happens before storage |
| every request appears in the log | the denominator for the reported rates exists |

Both are offline and deterministic; neither touches the network.

---

## 8. Relationship to the production deployment

The deployed service runs inside an institutional platform. This repository is a
re-implementation of its diagnostic workflow, not a mirror of the platform.

Removed, because they are platform infrastructure and not part of the workflow:

| Removed | Replaced by |
|---|---|
| in-house parent POM and private artifacts | Spring Boot parent, Maven Central only |
| Dubbo RPC for the knowledge service | `KnowledgeService` interface, local JDBC implementation |
| Nacos configuration and registry | `application*.yml` and environment variables |
| MyBatis-Plus | `JdbcTemplate` |
| Beetl templates and a large static bundle | one self-contained HTML page |
| `system`-scoped local jar | no local jars; the project builds from a clean machine |

What is preserved, because it is what the paper describes: the routing table, both
recognition contracts, the knowledge lookup rule, the response shape, and the
access-log fields used for reporting.

A reader can therefore verify the workflow and the contracts, and cannot verify
the platform plumbing. That boundary is stated here rather than implied.

---

## 9. Data

`schema.sql` and `data.sql` create and seed an in-memory database at start-up.
The 24 seed rows are hand-written examples covering all ten categories; they are
not the production knowledge base and are far too few to measure retrieval
quality. Column names and meanings match the production table, so the JSON
contract is identical in both deployments. The data dictionary is in
[docs/KNOWLEDGE_BASE.md](docs/KNOWLEDGE_BASE.md).

The production knowledge base is not redistributed here. It is a licensed
institutional asset; the redistribution terms are documented in
[docs/KNOWLEDGE_BASE.md](docs/KNOWLEDGE_BASE.md).

---

## 10. Desensitization

All credentials, internal addresses and institution-specific identifiers were
removed or replaced by environment variables. The record of what was changed, and
why, is in [docs/DESENSITIZATION.md](docs/DESENSITIZATION.md). No real value
appears anywhere in this repository, including its history, because the repository
history was restarted rather than rewritten.

---

## 11. Note for the metadata table

The code metadata table in the paper currently states JDK 8. This build requires
**JDK 17**, because Spring Boot 3.3 and the Jakarta EE namespace require it. The
table should be updated before resubmission; the requirement is asserted by
`pom.xml` (`java.version=17`, `maven-compiler-plugin` release 17) and by CI-style
verification in `scripts/smoke-test.sh`.

---

## 12. License and contact

MIT, see [LICENSE](LICENSE). The license covers the code in this repository. It
does not grant rights to the third-party recognition services or to the
production knowledge base, which are addressed separately in the documents above.

The copyright line in [LICENSE](LICENSE) is a placeholder: the names of the
authors and their affiliation appear on the paper, not in the repository.

Issues and questions: open an issue on the repository.

---

## 中文快速开始

环境要求：JDK 17 + Maven 3.9，不需要数据库、注册中心或任何识别服务账号。

```bash
git clone https://github.com/yanfangwang05/plantdoctor.git
cd plantdoctor
mvn clean package
java -jar target/plantdoctor-1.0.0.jar      # 默认 mock 模式
```

浏览器打开 <http://localhost:8080>，选择作物/类型、上传图片即可看到完整流程：
识别标签 → 知识关联 → 访问日志。

命令行验证：

```bash
curl -F "cropType=WHEAT" -F "file=@samples/wheat_rust.jpg" http://localhost:8080/api/diagnosis
./scripts/smoke-test.sh          # 编译 + 启动 + 25 项断言 + 关闭
```
