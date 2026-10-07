# Kế hoạch module `document` mức 1: template trong code, phát hành tài liệu chính thức có snapshot

> Dành cho người triển khai: thực hiện lần lượt các task bằng `superpowers:executing-plans`; chỉ dùng subagent khi được yêu cầu. Đây là kế hoạch, không xác nhận runtime đã triển khai hoặc tests đã chạy.

**Ngày:** 2026-10-07 (sửa cùng ngày: thu hẹp về mức 1)

**Mục tiêu:** Mỗi tài liệu chính thức đã phát hành (phiếu khám sức khỏe, kết quả, đơn thuốc, kết luận) truy vết được tới đúng dữ liệu nguồn, đúng template version và đúng file PDF đã giao. Template do dev quản lý trong repo; đổi layout thì release code.

**Không làm ở mức 1:** phòng khám tự upload template, validate file người dùng, preview UI, màn quản trị template. Xem [§9 Lên mức 2](#9-lên-mức-2).

**Thời điểm triển khai:** cùng với **luồng phát hành chính thức đầu tiên** (Q4). Không làm trước khi có nhu cầu phát hành, vì hiện chưa có use case nào ghi `issued_representations`.

**Bối cảnh:** Schema clean-slate đã có `files`, `templates`, `template_versions` (hiệu lực theo `active_from`/`retired_at`, trigger chống chồng thời gian), `service_template_mappings`, `issued_representations` (bất biến, một nguồn version, unique theo `(nguồn, document_type)`). Code `document` mới có `MasterHealthExaminationTemplateQuery` (chưa nơi nào dùng). Frontend đang in bằng `window.print()` trên HTML mock.

**Kiến trúc:** module nghiệp vụ sở hữu dữ liệu và quyết định phát hành; `document` sở hữu template catalog, contract registry, render, lưu file và bản ghi phát hành. Phụ thuộc một chiều: `healthexamination`/`diagnostics`/`prescription`/`clinical` → `document`. `document` không import module nghiệp vụ.

```text
src/main/resources/document-templates/
  catalog.yaml                       (manifest: loại, version, file, activeFrom, contractVersion)
  health-exam-master-form/v1.docx    (không bao giờ sửa sau khi đã đăng ký)
            │  khởi động
            ▼
TemplateCatalogRegistrar  ──► templates / files(CLASSPATH) / template_versions
                                      ▲
Business module                       │ chọn version hiệu lực
  ├─ DocumentTypeContract (record)    │
  ├─ dựng model đã format sẵn ──► document::rendering.render()  ── DOCX (poi-tl) → PDF (Gotenberg) → DocumentStorage
  └─ transaction phát hành ─────► document::rendering.record()  ── files + issued_representations + audit
```

## 1. Phạm vi đầu ra

| Loại | Đi qua `document`? | Ghi chú |
|---|---|---|
| Tài liệu chính thức: phiếu khám sức khỏe (`MASTER_FORM`), kết quả CLS (`ONE_PER_SERVICE`/`MERGE_BY_TEMPLATE`), đơn thuốc, kết luận | **Có** | Template version + PDF snapshot |
| Excel chi tiết khám (xuất/nhập), Excel mẫu nhập người khám, Word báo cáo thanh toán | **Không** | Sinh bằng code trong `healthexamination` theo plan [20261007-examination-detail-excel-and-report-word](20261007-examination-detail-excel-and-report-word.md) (E13). File Excel được nhập lại nên layout chính là contract |

## 2. Quyết định

| # | Quyết định | Lý do / phương án bị loại |
|---|---|---|
| D1 | Template `.docx` nằm trong `src/main/resources/document-templates/`, khai báo trong `catalog.yaml`. File đã đăng ký là **bất biến**: sửa layout thì thêm `v{N+1}.docx` | Thay đổi đi qua code review + CI. Không có đường cho file lạ vào hệ thống |
| D2 | `TemplateCatalogRegistrar` chạy khi khởi động (một transaction, `pg_advisory_xact_lock` để nhiều instance không đăng ký trùng): tạo `templates` còn thiếu; đăng ký version mới vào `files` (`storage_provider='CLASSPATH'`, `storage_key` = đường dẫn resource, SHA-256) và `template_versions`; set `retired_at` của version trước = `active_from` của version mới | Không phải viết checksum tay trong SQL. Flyway vẫn chỉ lo schema |
| D3 | Registrar **dừng startup** nếu: checksum của version đã đăng ký khác file trong repo; DB có version mà manifest không còn; `version_no` không liên tục; contract version của version đang/chờ hiệu lực không khớp code (D6) | Không ai sửa lén được v1 đã dùng để in |
| D4 | `activeFrom` trong manifest là tuỳ chọn. Nếu trống hoặc đã ở quá khứ lúc đăng ký thì dùng **thời điểm đăng ký** | Tránh hồi tố: tài liệu đã phát hành bằng v1 trong khoảng deploy trễ vẫn nằm đúng trong thời gian hiệu lực của v1 |
| D5 | **Data contract nằm trong code**, do module nghiệp vụ sở hữu: một Java record cho mỗi `document_type` + `contractVersion`. Registry trích danh sách placeholder bằng reflection. **Golden test** commit danh sách placeholder để mọi thay đổi record hiện ra trong diff | Contract không trôi khỏi code |
| D6 | Tiến hóa contract: **thêm field tuỳ chọn** thì giữ version. **Đổi tên/xóa/đổi kiểu, hoặc thêm field bắt buộc** thì tăng `contractVersion` và phải thêm template version mới khai báo version đó trong cùng change | Không deploy được code làm hỏng template đang dùng |
| D7 | **Không có logic trong template.** Giá trị được format sẵn trong model (ngày `dd/MM/yyyy` theo Asia/Ho_Chi_Minh, tiền `1.250.000`, số tiền bằng chữ, null hiển thị `—`). Template chỉ có: scalar, lặp dòng bảng, khối điều kiện boolean, ảnh. **Tắt SpEL** | Dễ kiểm thử, không phụ thuộc engine |
| D8 | Thông tin hay đổi **không nằm cứng trong template**: tên/địa chỉ/SĐT/thành phố/logo phòng khám đến từ cấu hình `nkc.document.clinic.*`. `document` tự gắn khối `clinic.*` vào mọi model. Người ký lấy từ dữ liệu nguồn | Đổi thông tin phòng khám chỉ cần đổi cấu hình rồi restart, không cần release. Gom `nkc.report.clinic` hiện có về một nguồn (T6) |
| D9 | Renderer: **Apache POI 5.5.1 (đã có) + poi-tl**, phải spike tương thích trước (T1). Nếu spike fail thì tự viết engine nhỏ trên XWPF (gộp run + scalar + lặp dòng bảng + ảnh). **Không dùng docx4j** | Word hay cắt `{{patient.fullName}}` thành nhiều run; poi-tl xử lý được việc này |
| D10 | **PDF qua Gotenberg** (LibreOffice trong container riêng, gọi HTTP có timeout), image tự build có font tiếng Việt | Chuyển DOCX→PDF bằng Java thuần cho chất lượng kém; không nhét LibreOffice vào image backend |
| D11 | **Phát hành thì bắt buộc lưu PDF snapshot** (`issued_representations.rendered_file_id`). Xem trước khi phát hành thì render-on-demand, không lưu, audit `DOCUMENT_RENDERED` | Render lại về sau có thể khác (font, thư viện). **Cần owner amendment**: [04-persistence](../architecture/04-persistence.md) đang ghi "render-on-demand, không có generated-document table". Ở đây chỉ thêm cột, không thêm bảng |
| D12 | **In lại = trả đúng byte đã lưu** (kiểm checksum). Template version mới **không** phát hành lại tài liệu cũ. Chỉ khi nguồn có version mới (đính chính) mới phát hành tài liệu mới | Khớp với unique index `(nguồn, document_type)` sẵn có |
| D13 | Phát hành **nguyên tử**: `render()` (chọn version, DOCX→PDF, put object) chạy **trước** transaction. Sau đó một transaction của module nghiệp vụ gồm: đổi trạng thái nguồn → `record()` (insert `files`, `issued_representations`) → audit `DOCUMENT_ISSUED`. Endpoint phát hành nhận `Idempotency-Key` | Không có trạng thái "đã phát hành nhưng thiếu file"; gọi chậm ra ngoài không nằm trong transaction (PROJECT_RULES §21, §23) |
| D14 | `DocumentStorage` port: `CLASSPATH` (chỉ đọc, cho template), `LOCAL` (dev), `S3` (S3-compatible: R2/MinIO/S3) cho PDF phát hành. Key không chứa PII: `issued/{yyyy}/{mm}/{uuid}.pdf`. Tải file luôn đi qua backend có kiểm quyền, `Cache-Control: no-store` | Cột `storage_provider` đã có |
| D15 | Không thêm `status`/DRAFT/DISCARDED/`validation_report` ở mức 1: mọi version trong DB đều đã được dev duyệt qua code review | Lên mức 2 chỉ cần thêm cột `status` với mặc định `PUBLISHED` |

## 3. Hiện trạng đã đối chiếu source

| Thành phần | Có sẵn | Cần bổ sung |
|---|---|---|
| `template_versions` | `version_no`, `file_id`, `paper_size`, `orientation`, `render_mode`, `active_from`, `retired_at`; trigger chống chồng thời gian | `contract_version` |
| Trigger bất biến | `trg_protect_issued_template_version` chặn **mọi** UPDATE/DELETE khi version đã được dùng | **Lỗi chặn lifecycle**: không set được `retired_at` cho v1 đã dùng khi thêm v2. Viết lại (§4) |
| `issued_representations` | Bất biến, `template_version_id`, `issued_by/at`, trigger kiểm trạng thái nguồn | `rendered_file_id` (NOT NULL, FK `files`, unique) |
| `files` | `storage_provider`, `storage_key`, `checksum bytea`; trigger khóa khi được template version tham chiếu | Trigger khóa thêm khi được `issued_representations.rendered_file_id` tham chiếu |
| Code `document` | Records 5 bảng, `MasterHealthExaminationTemplateQuery` | Contract registry, catalog registrar, storage, engine, PDF, facade |
| POI | `poi-ooxml` 5.5.1, `PoiPaymentReportDocxWriter` | poi-tl (sau spike); có thể cần `poi-ooxml-full` |
| Hạ tầng | docker-compose: backend, postgres, redis | Service `gotenberg`; MinIO cho test S3 |
| Cấu hình | `nkc.report.clinic.*` (`ReportClinicProperties`, trong `healthexamination`) | `nkc.document.clinic.*` + logo; báo cáo thanh toán đọc cùng nguồn |
| Schema contract tests | So khớp cột/thứ tự/kiểu của mọi record | Cập nhật `TemplateVersionRecord`, `IssuedRepresentationRecord` + baseline |

## 4. Migration `V007__document_issue_snapshot.sql` (phác thảo)

```sql
-- Chưa có use case ghi issued_representations; fail-fast nếu có dữ liệu ngoài dự kiến.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM public.issued_representations) THEN
    RAISE EXCEPTION 'V007 expects no issued representations';
  END IF;
END $$;

ALTER TABLE public.template_versions
  ADD COLUMN contract_version integer NOT NULL DEFAULT 1,
  ADD CONSTRAINT ck_document_template_versions_contract CHECK (contract_version > 0);
ALTER TABLE public.template_versions ALTER COLUMN contract_version DROP DEFAULT;

ALTER TABLE public.issued_representations
  ADD COLUMN rendered_file_id uuid NOT NULL REFERENCES public.files(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX ux_document_issued_rep_rendered_file
  ON public.issued_representations(rendered_file_id);
```

Trigger (`CREATE OR REPLACE` / thay thế):

1. Thay `trg_protect_issued_template_version` bằng `trg_guard_template_version_mutation`:
   - DELETE: luôn chặn.
   - UPDATE: chỉ cho đổi `retired_at` từ NULL sang giá trị (> `active_from`, đã có check). Mọi cột khác bất biến, kể cả khi version chưa được dùng.
2. `trg_protect_referenced_file`: chặn thêm khi file được `issued_representations.rendered_file_id` tham chiếu.
3. Trigger chống chồng thời gian giữ nguyên.

## 5. Thiết kế module

```text
document/
├── application/
│   ├── contract/         [@NamedInterface "contract"] DocumentTypeContract<M>, PlaceholderSpec
│   ├── rendering/        [@NamedInterface "rendering"] DocumentIssuer, RenderedDocument,
│   │                     DocumentSourceRef (sealed: RecordSnapshot | RecordVersion | ResultVersion | ...),
│   │                     IssuedDocumentReader (in lại)
│   ├── port/             DocumentStorage, DocxTemplateEngine, PdfConverter
│   └── query/            MasterHealthExaminationTemplateQuery (đã có), EffectiveTemplateQuery
├── domain/
│   ├── valueobject/      EffectivePeriod, StoredFileRef, TemplateVersionRef
│   └── repository/       TemplateCatalogRepository, StoredFileRepository, IssuedDocumentRepository
└── infrastructure/
    ├── persistence/      record/mapper/repository/converter
    ├── storage/          ClasspathDocumentStorage, LocalDocumentStorage, S3DocumentStorage, DocumentStorageProperties
    ├── rendering/        PoiTlDocxTemplateEngine, GotenbergPdfConverter, ClinicProfileProperties
    └── configuration/    TemplateContractRegistry, TemplateCatalogRegistrar, TemplateCatalogManifest
```

**Manifest:**

```yaml
templates:
  - code: HEALTH_EXAM_MASTER_FORM
    name: Giấy khám sức khỏe
    documentType: HEALTH_EXAM_MASTER_FORM
    renderMode: MASTER_FORM
    versions:
      - no: 1
        file: health-exam-master-form/v1.docx
        contractVersion: 1
        paperSize: A4
        orientation: PORTRAIT
        activeFrom: null        # null = thời điểm đăng ký (D4)
```

**Contract (ví dụ, nằm ở module nghiệp vụ):**

```java
public record MasterFormModel(
    PatientBlock patient, ExaminationBlock examination,
    List<ServiceResultRow> services, SignatureBlock doctor) {}   // clinic.* do document tự gắn (D8)

@Component
class MasterFormContract implements DocumentTypeContract<MasterFormModel> {
  public String documentType() { return "HEALTH_EXAM_MASTER_FORM"; }
  public int contractVersion() { return 1; }
  public Class<MasterFormModel> modelType() { return MasterFormModel.class; }
  public MasterFormModel sampleModel() { /* fixture, không dữ liệu thật */ }
  public Set<String> requiredPaths() { return Set.of("patient.fullName", "examination.conclusion", "doctor.fullName"); }
}
```

**Facade phát hành (D13):**

```java
RenderedDocument render(String documentType, Object model);                                // ngoài tx
IssuedDocumentRef record(RenderedDocument doc, DocumentSourceRef src, UUID actorAccountId); // trong tx của caller
StoredDocument open(UUID issuedRepresentationId);                                           // in lại; caller kiểm quyền
```

Nếu transaction rollback sau khi object đã được put thì object thành mồ côi; job dọn dẹp (T8) xóa object `issued/` cũ hơn 24h mà không có dòng `files` tương ứng.

## 6. Quy trình đổi mẫu (runbook cho dev)

1. Mở `vN.docx` bằng Word, sửa layout, **lưu thành** `v{N+1}.docx`. Không sửa `vN.docx`.
2. Thêm version vào `catalog.yaml` (để `activeFrom` trống nếu hiệu lực ngay khi deploy).
3. Chạy `./mvnw verify`: golden placeholder, render fixture cho mọi version đang/chờ hiệu lực, registrar test.
4. Mở PDF fixture do test sinh ra (`target/document-previews/`) để duyệt bằng mắt; đính kèm vào PR.
5. Merge, deploy. Registrar đăng ký v{N+1}, retire vN.

Quay lại mẫu cũ: thêm `v{N+2}.docx` là bản copy của file cũ, không xóa hay sửa version nào.

## 7. Tasks

| Task | Nội dung | Xong khi |
|---|---|---|
| **T0** | Owner chốt Q1–Q5 (§8). Viết **ADR-0015 Document rendering & storage** (D1–D15). Amend [04-persistence](../architecture/04-persistence.md), [01-overview](../architecture/01-overview.md), [03-domain-and-workflows](../architecture/03-domain-and-workflows.md) về snapshot PDF | ADR accepted |
| **T1** | Spike (≤ 2 ngày): poi-tl + POI 5.5.1 trên Java 25 (placeholder bị cắt run, lặp dòng bảng, ảnh logo, tiếng Việt có dấu); Gotenberg + font tiếng Việt, đo thời gian DOCX→PDF 2 trang. Ghi kết quả vào ADR | Có quyết định engine |
| **T2** | Migration V007 + trigger; cập nhật records, mapper XML, schema contract tests; sửa `MasterHealthExaminationTemplateQuery` nếu cần | Testcontainers: retire được v1 đã dùng; update cột khác bị chặn; xóa file đã phát hành bị chặn |
| **T3** | `DocumentStorage` + Classpath/Local/S3 adapter | Test S3 với MinIO Testcontainers; checksum khớp |
| **T4** | `TemplateContractRegistry` + golden test; `TemplateCatalogRegistrar` + manifest | Test: đăng ký mới, retire version cũ, idempotent khi khởi động lại, fail khi checksum lệch / thiếu version / contract lệch, 2 instance song song |
| **T5** | `DocxTemplateEngine` + `PdfConverter` (timeout, lỗi trả 503 có mã); test render fixture xuất PDF vào `target/document-previews/` | Mở lại DOCX bằng XWPF; PDF không rỗng; tiếng Việt đúng |
| **T6** | Facade `render`/`record`/`open`; `ClinicProfileProperties` (`nkc.document.clinic.*`), chuyển báo cáo thanh toán sang đọc cùng nguồn | Unit + integration test; Modulith verify |
| **T7** | Pilot end-to-end cho **một** loại tài liệu (Q4): contract + model builder + template v1 ở module nghiệp vụ, endpoint phát hành (idempotent) + in lại; FE thay `window.print()` mock bằng mở PDF | Transaction nguyên tử (rollback thì không có dòng nào); in lại trả đúng byte |
| **T8** | Vận hành: service `gotenberg` trong docker-compose/prod, cấu hình S3/R2 (mã hóa phía server, backup bucket), job dọn object mồ côi, runbook §6 vào `docs/architecture/06-testing-and-operations.md` | Runbook |
| **T9** | Mở rộng: kết quả CLS (`ONE_PER_SERVICE`/`MERGE_BY_TEMPLATE` qua `service_template_mappings`), đơn thuốc, kết luận | Mỗi loại một plan ngắn, tái dùng T2–T6 |

Thứ tự: T0 → T1 → (T2 ∥ T3) → T4 → T5 → T6 → T7 → T8. T2–T6 kiểm thử được bằng fixture, không cần chờ luồng hồ sơ khám.

## 8. Câu hỏi cần owner chốt (T0)

| # | Câu hỏi | Đề xuất |
|---|---|---|
| Q1 | Duyệt amendment: lưu PDF snapshot khi phát hành (thêm cột `rendered_file_id`) | Đồng ý |
| Q2 | Thêm sidecar Gotenberg vào hạ tầng | Đồng ý |
| Q3 | Font chuẩn của phòng khám (Times New Roman? bản quyền trên Linux) | Font metric-compatible (Liberation Serif/Tinos) hoặc font có license |
| Q4 | Loại tài liệu pilot cho T7 | Phiếu khám sức khỏe (`MASTER_FORM`) khi luồng phát hành hồ sơ khám sẵn sàng; nếu chưa thì đơn thuốc |
| Q5 | Storage production: R2, S3 hay ổ đĩa server | S3-compatible (R2) + backup; Local chỉ dùng dev |

## 9. Lên mức 2

Làm khi có ít nhất một tín hiệu: phòng khám cần đổi mẫu nhiều lần mỗi năm và chờ release là nút thắt; cần mẫu riêng theo từng công ty đối tác; nhân viên muốn tự sửa mẫu.

Mức 2 chỉ **thêm**, không làm lại:

- Cột `status` (`DRAFT`/`PUBLISHED`/`DISCARDED`, mặc định `PUBLISHED` cho dữ liệu mức 1), `active_from` cho phép NULL khi DRAFT, trigger chống chồng chỉ xét PUBLISHED, `validation_report`, `created_by`, `published_by/at`.
- Upload pipeline: `OoxmlPackageGuard` (chuyển sang `shared/infrastructure/ooxml`) + chặn macro/external relationship/altChunk/OLE → trích placeholder → validate với contract → render fixture → DRAFT có báo cáo → publish.
- API `/api/v1/document-templates` (danh sách, contract, lịch sử, upload, preview, tải file, publish, discard), permission `DOCUMENT_TEMPLATE_READ/MANAGE/PUBLISH`, màn **Mẫu tài liệu** ở FE.
- Registrar giữ lại cho template mặc định đi kèm code; version upload có `storage_provider` = `S3`/`LOCAL`.

## 10. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| poi-tl không tương thích POI 5.5.1 hoặc Java 25 | T1 spike trước; phương án dự phòng là engine XWPF tự viết |
| `poi-ooxml-lite` thiếu class lúc runtime | Test bắt buộc mở lại file đã sinh; nếu cần thì thêm `poi-ooxml-full` cùng version |
| PDF khác Word (font, ngắt trang) | Duyệt PDF fixture trong PR (§6 bước 4); font cố định trong image Gotenberg |
| Gotenberg chết hoặc chậm | Timeout + 503 rõ ràng; phát hành không nửa vời vì render nằm trước transaction |
| Dev sửa nhầm file version cũ | Registrar kiểm checksum và dừng startup (D3); test cùng logic chạy trong CI |
| Lộ PII qua log, key hoặc tên file | Key theo UUID; logging theo PROJECT_RULES §27 |

## 11. Ngoài phạm vi

Mọi hạng mục mức 2 (§9); template Excel; trình soạn WYSIWYG; chữ ký số PDF (khi làm thì snapshot D11 đã là điều kiện cần); đa ngôn ngữ; phát hành lại tài liệu cũ theo template mới.
