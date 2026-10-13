package com.ngockhanh.clinic.document.application.port;

/**
 * A picture frame in a Word template that is replaced when the document is filled. The template
 * author marks it by writing {@code {{@key}}} as the alternative text of a picture; the frame keeps
 * its size and position.
 *
 * @param key placeholder key without the leading {@code @}, such as {@code participant.barcode}
 * @param widthMm width of the frame in millimetres
 * @param heightMm height of the frame in millimetres
 */
public record ImageSlot(String key, double widthMm, double heightMm) {}
