# Third-party recognition contracts

PlantDoctor has exactly one external dependency: a commercial image-recognition
service for crop pests, diseases and weeds. This document records the contract the
code implements, with all identifiers removed.

Nothing here is a secret. Host names, paths, client identifiers and the license
string are issued to individual institutions and are read from the environment at
start-up; see `.env.example` and section 5 of the README.

---

## 1. Why the provider is not redistributed

The recognition service is a paid, licensed product. It cannot be bundled, and its
credentials cannot be published. The workflow around it - routing, validation,
knowledge linkage, logging - is what this repository reproduces, and it is
reproduced with the real code.

Two consequences, stated once and applied throughout:

* The repository ships a `mock` provider so the workflow runs offline. Its labels
  are fixtures, not predictions (README section 6).
* In `prod`, a missing credential produces an explicit error. There is no silent
  fallback to fixtures, because a fixture label presented as a production result
  would be a misrepresentation.

---

## 2. Two contracts, ten categories

The provider exposes two different endpoints. Which one applies is decided by the
diagnostic category, not by the image:

| Contract | Categories | Transport | Identifier |
|---|---|---|---|
| general | apple, citrus, grape, rice, wheat, insect pest, fall armyworm, grass weed | JSON over HTTPS, image by URL | `crop_id` |
| dedicated | corn, soybean | multipart form data, image bytes | `imageType` |

The reason for two contracts is coverage: the general endpoint has no model for
corn or soybean, so the provider offers a separate one. The mapping itself is in
`InsectCropType` and readable at runtime from `GET /api/crop-types`.

---

## 3. General contract

### 3.1 Token exchange

```
POST {base-url}{token-path}
Content-Type: application/x-www-form-urlencoded

client_id=<issued by vendor>&client_secret=<issued by vendor>
```

Response:

```json
{
  "code": 0,
  "data": {
    "access_token": "<opaque>",
    "token_type": "Bearer",
    "expires_in": 86400
  }
}
```

`code` other than `0` is a failure. The token is cached for 23 hours, one hour
short of the stated lifetime, so that a clock difference or a slow request cannot
turn a valid token into an expired one. The cache is refreshed under a lock, so a
burst of requests triggers one exchange, not one per request.

### 3.2 Prediction

```
POST {base-url}{predict-path}
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded

crop_id=<see routing table>&image_url=<public URL of the uploaded image>
```

Response:

```json
{
  "status": "1",
  "content": {
    "result": "<label>",
    "score": 0.9899
  }
}
```

* `status` is a string, not a number. `"1"` means the request was processed.
* `content.result` is a Chinese pest, disease or weed name. It is not an
  identifier, a code or a taxonomy path.
* `content.score` is a confidence value in `[0, 1]`. It is the provider's own
  score and is reported as `trustLevel`; it is not calibrated probability and
  should not be described as one.

Because the image is fetched by URL, this contract requires
`PLANTDOCTOR_PUBLIC_BASE_URL` to point at this application and `/uploads/**` to be
reachable from the vendor. When that is not the case the request fails with an
explicit message rather than sending a local path.

### 3.3 Failure semantics

| Situation | Result |
|---|---|
| `code != 0` on token exchange | failure, reason includes the provider payload |
| `status != "1"` | failure, payload truncated to 200 characters |
| `content.result` absent | failure, "returned no label" |
| transport error | failure, exception message |
| non-JSON payload | failure, "returned a non-JSON payload" |

A failure is returned to the caller and written to the access log. It is never
replaced by an empty or default label, because a default label would be
indistinguishable from a prediction.

---

## 4. Dedicated contract

```
POST {dedicated-url}
Content-Type: multipart/form-data

imageType=<15 for soybean, 14 for corn>
imageFile=<the image bytes>
license=<license string issued to the institution>
```

Response:

```json
{
  "returnResult": {
    "returnStatus": "1",
    "returnString": "<label>,<extra>",
    "returnMessage": "<reason when returnStatus != 1>"
  }
}
```

`returnStatus` is a string. `returnString` is comma-separated and only the first
field is the label; the remaining fields carry provider-specific detail and are
kept verbatim in the access log (`responseText`) for auditing.

This contract returns no confidence score. The diagnosis response therefore omits
`trustLevel` for corn and soybean, rather than inventing one.

---

## 5. Credentials

| Value | Where it comes from | How it reaches the process |
|---|---|---|
| client identifier and secret | vendor, per institution | `RECOGNITION_CLIENT_ID`, `RECOGNITION_CLIENT_SECRET` |
| license string | vendor, per institution | `CORN_SOY_LICENSE` |
| endpoint addresses | vendor documentation | `RECOGNITION_BASE_URL`, `RECOGNITION_TOKEN_PATH`, `RECOGNITION_PREDICT_PATH`, `CORN_SOY_URL` |

None of these appear in the repository, in `application*.yml`, in fixtures or in
test data. Tests run in `mock` mode and never reach the network.

Requesting access is a commercial process outside the scope of this repository.
A reader without credentials can still run every part of the workflow except the
vendor call itself.

---

## 6. Replacing the provider

The boundary is one interface:

```java
public interface RecognitionService {
    String providerId();
    RecognitionResult recognize(Path imagePath, String originalFilename, InsectCropType type);
}
```

An implementation is selected by `plantdoctor.recognition.mode`, so adding a
provider is a new class and a new value, with no change to controllers, routing,
linking or logging. `MockRecognitionService` is a working example of a second
implementation.

A replacement must return a label in the same form (a Chinese name usable as a
knowledge key) or the linkage step will report every request as unmatched, which
is visible in the log rather than silently tolerated.
