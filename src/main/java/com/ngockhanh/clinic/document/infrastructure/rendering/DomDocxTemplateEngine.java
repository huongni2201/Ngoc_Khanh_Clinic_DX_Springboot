package com.ngockhanh.clinic.document.infrastructure.rendering;

import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.ImageSlot;
import com.ngockhanh.clinic.document.application.port.PageLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Fills {@code {{key}}} placeholders by editing the OOXML parts directly with the JDK XML parser.
 * Word often splits one placeholder over several runs ({@code <w:r>}); the text of a paragraph is
 * therefore joined, matched, and written back run by run, so the formatting of the run that holds
 * the opening braces survives and every other part of the file is copied byte for byte. Paragraphs of
 * text boxes are handled as paragraphs of their own. Nothing is evaluated: a value is only ever
 * inserted as text. Entry count and inflated size are capped to refuse a zip bomb.
 *
 * <p>A picture whose alternative text is {@code {{@key}}} is a picture placeholder: the picture is
 * replaced by a new image part linked from the same drawing, so the frame keeps its size, anchor and
 * wrapping. The original picture is no longer referenced by the drawing.
 */
public class DomDocxTemplateEngine implements DocxTemplateEngine {
  private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
  private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
  private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
  private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
  private static final String CONTENT_TYPES =
      "http://schemas.openxmlformats.org/package/2006/content-types";
  private static final String CONTENT_TYPES_PART = "[Content_Types].xml";
  private static final Pattern IMAGE_PLACEHOLDER =
      Pattern.compile(
          "\\s*\\{\\{@\\s*([A-Za-z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)\\s*}}\\s*");
  private static final double EMU_PER_MM = 36_000;
  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final Pattern PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)\\s*}}");
  private static final Pattern TEXT_PART =
      Pattern.compile("word/(document|header\\d*|footer\\d*|footnotes|endnotes)\\.xml");
  private static final int MAX_ENTRIES = 2_000;
  private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
  /** Two page sizes within this many twips (1/20 point) count as equal. */
  private static final int SIZE_TOLERANCE_TWIPS = 20;

  private static final double MM_PER_TWIP = 25.4 / 1440;
  /** Upper bound of one inserted value; far above any real field, it only bounds memory. */
  static final int MAX_VALUE_CHARS = 1_000;

  @Override
  public Set<String> placeholders(byte[] docx) {
    Set<String> keys = new TreeSet<>();
    for (Map.Entry<String, byte[]> part : unzip(docx).entrySet()) {
      if (!TEXT_PART.matcher(part.getKey()).matches()) continue;
      Document dom = parse(part.getValue());
      for (Element paragraph : paragraphs(dom)) {
        Matcher match = PLACEHOLDER.matcher(joined(ownTexts(paragraph)));
        while (match.find()) keys.add(match.group(1));
      }
      for (Slot slot : slots(dom)) keys.add(slot.key());
    }
    return keys;
  }

  @Override
  public List<ImageSlot> imageSlots(byte[] docx) {
    List<ImageSlot> result = new ArrayList<>();
    for (Map.Entry<String, byte[]> part : unzip(docx).entrySet()) {
      if (!TEXT_PART.matcher(part.getKey()).matches()) continue;
      for (Slot slot : slots(parse(part.getValue())))
        result.add(new ImageSlot(slot.key(), slot.widthMm(), slot.heightMm()));
    }
    return result;
  }

  @Override
  public PageLayout pageLayout(byte[] docx) {
    byte[] document = unzip(docx).get("word/document.xml");
    if (document == null) throw new IllegalArgumentException("Not a Word document");
    List<int[]> sizes = new ArrayList<>();
    var nodes = parse(document).getElementsByTagNameNS(W, "pgSz");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element size = (Element) nodes.item(i);
      Node section = size.getParentNode();
      // A tracked change keeps the previous section settings; they are not the live page size.
      if (section == null
          || !"sectPr".equals(section.getLocalName())
          || (section.getParentNode() != null
              && "sectPrChange".equals(section.getParentNode().getLocalName()))) continue;
      sizes.add(new int[] {twips(size, "w"), twips(size, "h")});
    }
    if (sizes.isEmpty()) throw new IllegalArgumentException("The document has no page size");
    int[] first = sizes.get(0);
    for (int[] other : sizes)
      if (Math.abs(other[0] - first[0]) > SIZE_TOLERANCE_TWIPS
          || Math.abs(other[1] - first[1]) > SIZE_TOLERANCE_TWIPS
          || (other[0] > other[1]) != (first[0] > first[1]))
        throw new IllegalArgumentException("The document mixes page sizes or orientations");
    return new PageLayout(first[0] * MM_PER_TWIP, first[1] * MM_PER_TWIP);
  }

  @Override
  public byte[] fill(byte[] docx, Map<String, String> values, ImageSource images) {
    Map<String, byte[]> parts = unzip(docx);
    ImageParts added = new ImageParts(parts);
    for (String name : new ArrayList<>(parts.keySet())) {
      if (!TEXT_PART.matcher(name).matches()) continue;
      Document dom = parse(parts.get(name));
      boolean changed = false;
      for (Element paragraph : paragraphs(dom)) changed |= fillParagraph(paragraph, values);
      List<Slot> slots = slots(dom);
      if (!slots.isEmpty()) {
        for (Slot slot : slots) fillSlot(slot, values, images, name, added);
        changed = true;
      }
      if (changed) parts.put(name, serialize(dom));
    }
    added.writeTo(parts);
    return zip(parts);
  }

  /** Puts the picture of one placeholder in its drawing, or removes the drawing. */
  private static void fillSlot(
      Slot slot, Map<String, String> values, ImageSource images, String part, ImageParts added) {
    String value = values == null ? null : values.get(slot.key());
    byte[] png =
        value == null || value.isBlank()
            ? null
            : images.image(slot.key(), value, new ImageSlot(slot.key(), slot.widthMm(), slot.heightMm()));
    if (png == null) {
      slot.drawing().getParentNode().removeChild(slot.drawing());
      return;
    }
    if (png.length > MAX_IMAGE_BYTES)
      throw new IllegalStateException("The picture for " + slot.key() + " is too large");
    String id = added.add(part, png);
    slot.blip().setAttributeNS(R, "r:embed", id);
    slot.blip().removeAttributeNS(R, "link");
    // The marker is only for the template author; it is not meant for a reader of the output.
    clearAlternativeText(slot.drawing());
  }

  private static void clearAlternativeText(Element drawing) {
    var nodes = drawing.getElementsByTagName("*");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      if ("docPr".equals(element.getLocalName()) || "cNvPr".equals(element.getLocalName()))
        if (IMAGE_PLACEHOLDER.matcher(element.getAttribute("descr")).matches())
          element.setAttribute("descr", "");
    }
  }

  /** A picture placeholder found in a part. */
  private record Slot(String key, double widthMm, double heightMm, Element drawing, Element blip) {}

  private static List<Slot> slots(Document dom) {
    List<Slot> result = new ArrayList<>();
    var nodes = dom.getElementsByTagNameNS(WP, "docPr");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element docPr = (Element) nodes.item(i);
      Matcher match = IMAGE_PLACEHOLDER.matcher(docPr.getAttribute("descr"));
      if (!match.matches()) continue;
      Element frame = (Element) docPr.getParentNode();
      Element extent = child(frame, WP, "extent");
      Node drawing = frame.getParentNode();
      if (extent == null
          || drawing == null
          || !W.equals(drawing.getNamespaceURI())
          || !"drawing".equals(drawing.getLocalName()))
        throw new IllegalArgumentException("The picture placeholder is not an inline picture");
      var blips = frame.getElementsByTagNameNS(A, "blip");
      if (blips.getLength() != 1)
        throw new IllegalArgumentException("The picture placeholder must hold one picture");
      result.add(
          new Slot(
              match.group(1),
              emu(extent, "cx") / EMU_PER_MM,
              emu(extent, "cy") / EMU_PER_MM,
              (Element) drawing,
              (Element) blips.item(0)));
    }
    return result;
  }

  private static Element child(Element parent, String namespace, String name) {
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
      if (n.getNodeType() == Node.ELEMENT_NODE
          && namespace.equals(n.getNamespaceURI())
          && name.equals(n.getLocalName())) return (Element) n;
    return null;
  }

  private static double emu(Element extent, String attribute) {
    try {
      double value = Double.parseDouble(extent.getAttribute(attribute).trim());
      if (value > 0) return value;
    } catch (NumberFormatException ignored) {
      // falls through to the error below
    }
    throw new IllegalArgumentException("The picture placeholder has no size");
  }

  /**
   * New image parts and the relationships that point to them. Parts are added to the package in one
   * go at the end, so the map being iterated is never changed.
   */
  private static final class ImageParts {
    private final Map<String, byte[]> existing;
    private final Map<String, byte[]> media = new LinkedHashMap<>();
    private final Map<String, Document> relationships = new LinkedHashMap<>();
    private int next = 1;

    ImageParts(Map<String, byte[]> existing) {
      this.existing = existing;
    }

    /** Stores the PNG next to {@code part} and returns the relationship id that targets it. */
    String add(String part, byte[] png) {
      String folder = part.substring(0, part.lastIndexOf('/') + 1);
      String name;
      do name = "nkc-image-" + next++ + ".png";
      while (existing.containsKey(folder + "media/" + name) || media.containsKey(folder + "media/" + name));
      media.put(folder + "media/" + name, png);

      String relsName = folder + "_rels/" + part.substring(folder.length()) + ".rels";
      Document rels =
          relationships.computeIfAbsent(
              relsName,
              key ->
                  existing.containsKey(key)
                      ? parse(existing.get(key))
                      : emptyRelationships());
      String id = uniqueId(rels);
      Element relationship = rels.createElementNS(PKG_REL, "Relationship");
      relationship.setAttribute("Id", id);
      relationship.setAttribute("Type", R + "/image");
      relationship.setAttribute("Target", "media/" + name);
      rels.getDocumentElement().appendChild(relationship);
      return id;
    }

    void writeTo(Map<String, byte[]> parts) {
      if (media.isEmpty()) return;
      for (Map.Entry<String, Document> rels : relationships.entrySet())
        parts.put(rels.getKey(), serialize(rels.getValue()));
      parts.putAll(media);
      parts.put(CONTENT_TYPES_PART, withPngContentType(parts.get(CONTENT_TYPES_PART)));
    }

    private static String uniqueId(Document rels) {
      Set<String> used = new TreeSet<>();
      var nodes = rels.getElementsByTagNameNS(PKG_REL, "Relationship");
      for (int i = 0; i < nodes.getLength(); i++)
        used.add(((Element) nodes.item(i)).getAttribute("Id"));
      int n = 1;
      while (used.contains("rIdNkc" + n)) n++;
      return "rIdNkc" + n;
    }

    private static Document emptyRelationships() {
      return parse(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<Relationships xmlns=\""
                  + PKG_REL
                  + "\"/>")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] withPngContentType(byte[] contentTypes) {
      if (contentTypes == null) throw new IllegalArgumentException("The file is not a Word document");
      Document dom = parse(contentTypes);
      var defaults = dom.getElementsByTagNameNS(CONTENT_TYPES, "Default");
      for (int i = 0; i < defaults.getLength(); i++)
        if ("png".equalsIgnoreCase(((Element) defaults.item(i)).getAttribute("Extension")))
          return contentTypes;
      Element png = dom.createElementNS(CONTENT_TYPES, "Default");
      png.setAttribute("Extension", "png");
      png.setAttribute("ContentType", "image/png");
      dom.getDocumentElement().insertBefore(png, dom.getDocumentElement().getFirstChild());
      return serialize(dom);
    }
  }

  private static boolean fillParagraph(Element paragraph, Map<String, String> values) {
    List<Element> texts = ownTexts(paragraph);
    String text = joined(texts);
    Matcher match = PLACEHOLDER.matcher(text);
    if (!match.find()) return false;

    int[] owner = new int[text.length()];
    int at = 0;
    for (int i = 0; i < texts.size(); i++) {
      int length = texts.get(i).getTextContent().length();
      for (int k = 0; k < length; k++) owner[at++] = i;
    }
    StringBuilder[] out = new StringBuilder[texts.size()];
    for (int i = 0; i < out.length; i++) out[i] = new StringBuilder();

    int copied = 0;
    do {
      for (int k = copied; k < match.start(); k++) out[owner[k]].append(text.charAt(k));
      // The value takes the formatting of the run that holds the opening braces.
      out[owner[match.start()]].append(clean(values.get(match.group(1))));
      copied = match.end();
    } while (match.find());
    for (int k = copied; k < text.length(); k++) out[owner[k]].append(text.charAt(k));

    for (int i = 0; i < out.length; i++) {
      String next = out[i].toString();
      if (next.equals(texts.get(i).getTextContent())) continue;
      texts.get(i).setTextContent(next);
      texts.get(i).setAttributeNS(XML_NS, "xml:space", "preserve");
    }
    return true;
  }

  /** Plain text only: line breaks and characters XML cannot hold become spaces. */
  static String clean(String value) {
    if (value == null) return "";
    if (value.length() > MAX_VALUE_CHARS) {
      int end = MAX_VALUE_CHARS;
      if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
      value = value.substring(0, end);
    }
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)
          && i + 1 < value.length()
          && Character.isLowSurrogate(value.charAt(i + 1))) {
        out.append(c).append(value.charAt(++i));
      } else if (Character.isSurrogate(c)
          || Character.isISOControl(c)
          || c == '\uFFFE'
          || c == '\uFFFF') {
        out.append(' ');
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static List<Element> paragraphs(Document dom) {
    var nodes = dom.getElementsByTagNameNS(W, "p");
    List<Element> result = new ArrayList<>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
    return result;
  }

  /** The {@code w:t} elements of a paragraph, not those of paragraphs nested in its text boxes. */
  private static List<Element> ownTexts(Element paragraph) {
    List<Element> texts = new ArrayList<>();
    collectTexts(paragraph, texts);
    return texts;
  }

  private static void collectTexts(Node parent, List<Element> texts) {
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNodeType() != Node.ELEMENT_NODE) continue;
      if (W.equals(child.getNamespaceURI())) {
        if ("p".equals(child.getLocalName())) continue;
        if ("t".equals(child.getLocalName())) {
          texts.add((Element) child);
          continue;
        }
      }
      collectTexts(child, texts);
    }
  }

  private static String joined(List<Element> texts) {
    StringBuilder text = new StringBuilder();
    for (Element element : texts) text.append(element.getTextContent());
    return text.toString();
  }

  private static int twips(Element size, String attribute) {
    String raw = size.getAttributeNS(W, attribute);
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("The document has no page size");
    }
  }

  private static Document parse(byte[] xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new IllegalArgumentException("The Word document is not valid", e);
    }
  }

  private static byte[] serialize(Document dom) {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      dom.setXmlStandalone(true);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(dom), new StreamResult(out));
      return out.toByteArray();
    } catch (TransformerException e) {
      throw new IllegalStateException("The Word document could not be written", e);
    }
  }

  private static Map<String, byte[]> unzip(byte[] docx) {
    Map<String, byte[]> parts = new LinkedHashMap<>();
    long total = 0;
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        if (parts.size() >= MAX_ENTRIES)
          throw new IllegalArgumentException("The Word document has too many parts");
        byte[] content = readLimited(in, MAX_TOTAL_BYTES - total);
        total += content.length;
        parts.put(entry.getName(), content);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("The file is not a Word document", e);
    }
    if (!parts.containsKey("word/document.xml"))
      throw new IllegalArgumentException("The file is not a Word document");
    return parts;
  }

  private static byte[] readLimited(InputStream in, long limit) throws IOException {
    byte[] content = in.readNBytes((int) Math.min(limit, Integer.MAX_VALUE - 8L) + 1);
    if (content.length > limit)
      throw new IllegalArgumentException("The Word document is too large when unpacked");
    return content;
  }

  private static byte[] zip(Map<String, byte[]> parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> part : parts.entrySet()) {
        zip.putNextEntry(new ZipEntry(part.getKey()));
        zip.write(part.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException("The Word document could not be written", e);
    }
    return out.toByteArray();
  }
}
