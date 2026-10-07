package com.ngockhanh.clinic.healthexamination.infrastructure.word;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code nkc.report.clinic.*} identity of the clinic printed in the header and the signature block
 * of the payment summary Word document. Nothing is hardcoded in the writer; a blank value is
 * simply left out of the document.
 *
 * @param name clinic name
 * @param address clinic address
 * @param phone clinic phone number
 * @param city place name of the "city, ngày dd tháng MM năm yyyy" line
 */
@ConfigurationProperties("nkc.report.clinic")
public record ReportClinicProperties(String name, String address, String phone, String city) {
  public ReportClinicProperties {
    name = name == null ? "" : name.strip();
    address = address == null ? "" : address.strip();
    phone = phone == null ? "" : phone.strip();
    city = city == null ? "" : city.strip();
  }
}
