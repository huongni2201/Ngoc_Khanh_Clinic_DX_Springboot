package com.ngockhanh.clinic.healthexamination.application.contract;

import com.ngockhanh.clinic.document.application.contract.DocumentTypeContract;
import com.ngockhanh.clinic.document.application.contract.PlaceholderSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Contract of the blank periodic health examination form (Mẫu số 03, Phụ lục XXIV, Thông tư
 * 32/2023/TT-BYT). {@code required} means the template file must contain the placeholder; it is
 * independent of whether a Participant has the data, which prints as a dotted line.
 *
 * <p>Version 1 may gain optional placeholders without a new version; renaming, removing, retyping
 * or adding a required one raises {@link #CONTRACT_VERSION}.
 */
@Component
public class PeriodicFormBlankContract implements DocumentTypeContract {
  public static final String DOCUMENT_TYPE = "HEALTH_EXAM_PERIODIC_FORM_BLANK";
  public static final int CONTRACT_VERSION = 1;

  private static final List<PlaceholderSpec> PLACEHOLDERS =
      List.of(
          new PlaceholderSpec("participant.code", "Mã người khám (số sổ)", "KSK-2026-001-0001", true),
          new PlaceholderSpec("participant.barcode", "Mã vạch Code128 của mã người khám (ảnh; đặt {{@participant.barcode}} làm văn bản thay thế của ảnh)", "KSK-2026-001-0001", false),
          new PlaceholderSpec("participant.fullName", "Họ và tên", "Nguyễn Văn A", false),
          new PlaceholderSpec("participant.fullNameUpper", "Họ và tên (chữ in hoa)", "NGUYỄN VĂN A", false),
          new PlaceholderSpec("participant.sex", "Giới tính (Nam/Nữ)", "Nam", false),
          new PlaceholderSpec("participant.dateOfBirth", "Ngày sinh", "01/02/1990", true),
          new PlaceholderSpec("participant.yearOfBirth", "Năm sinh", "1990", false),
          new PlaceholderSpec("participant.age", "Tuổi tại ngày khám", "36", false),
          new PlaceholderSpec("participant.identificationNumber", "Số CCCD/CMND", "001090000123", true),
          new PlaceholderSpec("participant.identificationIssueDate", "Ngày cấp CCCD", "15/06/2021", false),
          new PlaceholderSpec("participant.identificationIssuePlace", "Nơi cấp CCCD", "Cục Cảnh sát QLHC về TTXH", false),
          new PlaceholderSpec("participant.phone", "Số điện thoại", "0912345678", false),
          new PlaceholderSpec("participant.address", "Nơi ở hiện tại", "12 Lê Lợi, Quận 1, TP.HCM", false),
          new PlaceholderSpec("participant.ethnicity", "Dân tộc", "Kinh", false),
          new PlaceholderSpec("participant.workplace", "Nơi làm việc (mặc định là tên doanh nghiệp)", "Công ty TNHH ABC", false),
          new PlaceholderSpec("participant.departmentName", "Phòng ban", "Phòng Kế toán", false),
          new PlaceholderSpec("participant.positionName", "Nghề nghiệp, chức danh", "Kế toán", false),
          new PlaceholderSpec("participant.examinationDate", "Ngày khám", "20/10/2026", false),
          new PlaceholderSpec("organization.name", "Tên doanh nghiệp", "Công ty TNHH ABC", false),
          new PlaceholderSpec("organization.address", "Địa chỉ doanh nghiệp", "12 Lê Lợi, Quận 1, TP.HCM", false),
          new PlaceholderSpec("batch.code", "Mã đợt khám", "KSK-2026-001", false),
          new PlaceholderSpec("batch.name", "Tên đợt khám", "Khám sức khỏe định kỳ 2026", false),
          new PlaceholderSpec("printedDate", "Ngày in", "12/10/2026", false));

  @Override
  public String documentType() {
    return DOCUMENT_TYPE;
  }

  @Override
  public String templateCode() {
    return DOCUMENT_TYPE;
  }

  @Override
  public String templateName() {
    return "Phiếu khám sức khỏe định kỳ (trắng)";
  }

  @Override
  public int contractVersion() {
    return CONTRACT_VERSION;
  }

  @Override
  public String starterResource() {
    return "document-templates/health-exam-periodic-form-blank/health-examination-v1.docx";
  }

  /** The form is a folded A3 booklet: two landscape pages per Participant. */
  @Override
  public Integer pagesPerRecord() {
    return 2;
  }

  @Override
  public List<PlaceholderSpec> placeholders() {
    return PLACEHOLDERS;
  }
}
