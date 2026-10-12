package com.ngockhanh.clinic.shared.web;

/**
 * One rejected field of a request body or parameter.
 *
 * <p>Clients choose the wording by {@code field} and {@code code}. {@code message} is an English
 * technical detail for developers and must not be shown to end users.
 *
 * @param field JSON path or parameter name, for example {@code services[0].negotiatedPrice}
 * @param code failed constraint or conversion, for example {@code NotBlank}
 * @param message English technical detail
 */
public record FieldErrorDetail(String field, String code, String message) {}
