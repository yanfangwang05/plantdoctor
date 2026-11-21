# Sample images

Five 1x1 JPEG files, 160 bytes each.

They are not photographs, and that is deliberate:

* real photographs of diseased plants carry third-party rights that cannot be
  cleared for redistribution;
* the offline provider matches on file name, so the bytes have no effect on the
  result;
* a 160-byte file keeps the repository small and the diff reviewable.

Their only purpose is to give a first-time reader something to upload:

| File | Category to select | Label returned in `mock` mode |
|---|---|---|
| `wheat_rust.jpg` | Wheat | 小麦条锈病 |
| `corn_leaf_blight.jpg` | Corn | 玉米大斑病 |
| `fall_armyworm.jpg` | Fall armyworm | 草地贪夜蛾 |
| `corn_borer_alias.jpg` | Insect pest | 玉米钻心虫 (an alias, linked to 玉米螟) |
| `unknown_lesion.jpg` | Wheat | 未收录的叶部斑害 (no knowledge record) |

```bash
curl -F "cropType=WHEAT" -F "file=@samples/wheat_rust.jpg" \
     http://localhost:8080/api/diagnosis
```

## Using your own images

Copy them into this directory and either keep the file names above, or add entries
to `src/main/resources/mock/recognition.json`:

```json
"my_photo.jpg": {"insectName": "稻瘟病", "trustLevel": 0.95}
```

Restart the application after editing the fixture file; it is read once at
start-up.

With a real provider (`--spring.profiles.active=prod`) the file name is ignored
and the image content is what matters. Accuracy measured on these placeholders is
meaningless in either mode: in `mock` they return fixtures, and in `prod` a 1x1
pixel image contains no lesion to recognise.
