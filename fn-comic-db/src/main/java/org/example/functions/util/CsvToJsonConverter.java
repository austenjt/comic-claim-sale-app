package org.example.functions.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.example.functions.model.ComicBook;
import org.example.functions.model.CBCSCondition;
import org.example.functions.model.CGCCondition;
import org.example.functions.model.ComicCondition;
import org.example.functions.model.ComicNumber;
import org.example.functions.model.enums.ComicFields;
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.ConditionFields;
import org.example.functions.model.enums.GoCollectFields;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.model.enums.NumberSentinel;
import org.example.functions.model.enums.PageQuality;
import org.example.functions.model.thirdParty.GrandComicDBInfo;
import org.example.functions.model.thirdParty.GoCollectInfo;
import org.example.functions.service.ComicService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
public class CsvToJsonConverter {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final String csvData;
  private final ComicService comicService;

  public CsvToJsonConverter(String csvData, ComicService comicService) {
    this.csvData = csvData;
    this.comicService = comicService;
    log.info("CSV data cached.");
  }

  public Map<String, String> extractNumberFromTitle(String title) {
    Map<String, String> result = new HashMap<>();
    Pattern pattern = Pattern.compile("(#|\\s)(\\d+)");
    Matcher matcher = pattern.matcher(title);
    if (matcher.find()) {
      String matched = matcher.group(2);
      result.put("number", matched);
      String numberExtracted = StringUtils.replace(title, "#" + matched, "");
      numberExtracted = StringUtils.replace(numberExtracted, "  ", " ").trim();
      result.put("title", numberExtracted);
    } else {
      result.put("number", "-1");
      result.put("title", title.trim());
    }
    return result;
  }

  // --- Helper methods ---

  private BigDecimal parseBigDecimal(String value) {
    if (isBlank(value)) return null;
    try {
      return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Integer parseInteger(String value) {
    if (isBlank(value)) return null;
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private GradingCompany parseGradingCompany(String value) {
    if (isBlank(value)) return GradingCompany.NOT_CERTIFIED;
    try {
      return GradingCompany.valueOf(value.replace(" ", "_").toUpperCase());
    } catch (IllegalArgumentException e) {
      return GradingCompany.NOT_CERTIFIED;
    }
  }

  private ComicGrade parseComicGrade(String value) {
    if (isBlank(value)) return null;
    try {
      double gradeValue = Double.parseDouble(value);
      for (ComicGrade cg : ComicGrade.values()) {
        if (cg.getNumericGrade() == gradeValue) {
          return cg;
        }
      }
    } catch (NumberFormatException e) {
      // fall through
    }
    return null;
  }

  private PageQuality parsePageQuality(String value) {
    if (isBlank(value)) return null;
    try {
      return PageQuality.valueOf(value.replace("-", "_").replace(" ", "_").toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Boolean parseBoolean(String value) {
    if (isBlank(value)) return null;
    return Boolean.valueOf(value);
  }

  String reconcileTitleWithNumber(String title, ComicNumber comicNumber) {
    if (title == null || comicNumber == null || !comicNumber.hasStandardNumber()) return title;
    String trimmed = title.trim();
    // Strip " #<number>" from anywhere in the title (\b ensures we don't partially match e.g. #1 inside #10)
    String result = trimmed.replaceFirst("\\s*#" + comicNumber.getNumber() + "\\b", "")
                           .replaceAll("\\s{2,}", " ")
                           .trim();
    return result;
  }

  private ComicNumber parseComicNumber(String issueNumber, String title) {
    if (isNotBlank(issueNumber)) {
      try {
        return ComicNumber.of(-1, Integer.parseInt(issueNumber.trim()));
      } catch (NumberFormatException e) {
        // fall through to title extraction
      }
    }
    Map<String, String> extracted = extractNumberFromTitle(title);
    String numberStr = extracted.get("number");
    try {
      return ComicNumber.of(-1, Integer.parseInt(numberStr));
    } catch (NumberFormatException e) {
      return ComicNumber.of(-1, NumberSentinel.valueOf(numberStr.toUpperCase()));
    }
  }

  // --- Main method ---

  /**
   * Parses GoCollect CSV data and uploads comics to blob storage.
   * Loads existing comics once for in-memory duplicate checking to avoid
   * repeated blob downloads.
   *
   * @param request the HTTP request
   * @return an HTTP response with succeeded/failed/duplicate results
   * @throws JsonProcessingException if JSON serialization fails
   */
  public HttpResponseMessage loadGoCollectCsvData(HttpRequestMessage<Optional<String>> request) throws JsonProcessingException {
    log.info("Attempting to parse GoCollect CSV data...");
    List<ComicBook> loaded = new ArrayList<>();
    List<ComicBook> failedToLoad = new ArrayList<>();
    List<ComicBook> duplicates = new ArrayList<>();

    // Load existing comics once for duplicate checking
    Set<ComicBook> existingComics = new HashSet<>(comicService.getComicsList());
    log.info("Loaded {} existing comics for duplicate checking.", existingComics.size());

    try (CSVParser csvParser = CSVParser.parse(csvData, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
      for (CSVRecord record : csvParser) {
        try {
          ComicBook comicBook = new ComicBook();

          // Title and number
          String title = record.get(ComicFields.COMIC.col());
          String issueNumber = record.get(ComicFields.ISSUE_NUMBER.col());
          ComicNumber comicNumber = parseComicNumber(issueNumber, title);
          comicBook.setTitle(reconcileTitleWithNumber(title, comicNumber));
          comicBook.setNumber(comicNumber);

          // Series
          comicBook.setSeries(record.get(ComicFields.SERIES.col()));

          // GoCollect info
          String comicUrl = record.get(GoCollectFields.COMIC_URL.col());
          String gcSlug = isNotBlank(comicUrl) ? comicUrl.substring(comicUrl.lastIndexOf('/') + 1) : null;
          comicBook.setGoCollectInfo(GoCollectInfo.builder()
              .gcIndex(parseInteger(record.get(GoCollectFields.GCIN.col())))
              .gcSlug(gcSlug)
              .gcUrl(comicUrl)
              .gcSeries(record.get(ComicFields.SERIES.col()))
              .importDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
              .build());

          // GCDB info (defaults)
          comicBook.setGrandComicDBInfo(GrandComicDBInfo.builder()
              .gcdbIssueId(-1)
              .gcdbSeriesId(-1)
              .issueUrl("")
              .seriesUrl("")
              .build());

          // Defaults
          comicBook.setBarCode("");
          comicBook.setCollectionGroup(-1);

          // Financial
          comicBook.setPersonalEstimate(parseBigDecimal(record.get(GoCollectFields.PERSONAL_ESTIMATE.col())));
          comicBook.setTargetPrice(parseBigDecimal(record.get(GoCollectFields.TARGET_PRICE.col())));
          comicBook.setPricePaid(parseBigDecimal(record.get(GoCollectFields.PRICE_PAID.col())));

          // Grading & Condition
          GradingCompany certificationCompany = parseGradingCompany(record.get(ConditionFields.CERTIFICATION_COMPANY.col()));
          ComicCondition.ComicConditionBuilder condition = ComicCondition.builder()
              .certificationCompany(certificationCompany)
              .certificationId(record.get(ConditionFields.CERTIFICATION_ID.col()))
              .notCertifiedLabel(record.get(ConditionFields.NOT_CERTIFIED_LABEL.col()))
              .notCertifiedGrade(parseComicGrade(record.get(ConditionFields.NOT_CERTIFIED_GRADE.col())))
              .notCertifiedPageQuality(parsePageQuality(record.get(ConditionFields.NOT_CERTIFIED_PAGE_QUALITY.col())))
              .notCertifiedPedigree(record.get(ConditionFields.NOT_CERTIFIED_PEDIGREE.col()))
              .notCertifiedDegreeOfRestoration(record.get(ConditionFields.NOT_CERTIFIED_DEGREE_OF_RESTORATION.col()))
              .notCertifiedSignature(parseBoolean(record.get(ConditionFields.NOT_CERTIFIED_SIGNATURE.col())));
          if (certificationCompany == GradingCompany.CGC) {
              condition
                  .isGraded(true)
                  .cgcCondition(CGCCondition.builder()
                      .label(record.get(ConditionFields.CGC_LABEL.col()))
                      .grade(parseComicGrade(record.get(ConditionFields.CGC_GRADE.col())))
                      .pageQuality(parsePageQuality(record.get(ConditionFields.CGC_PAGE_QUALITY.col())))
                      .pedigree(record.get(ConditionFields.CGC_PEDIGREE.col()))
                      .signature(parseBoolean(record.get(ConditionFields.CGC_SIGNATURE.col())))
                      .degreeOfRestoration(record.get(ConditionFields.CGC_DEGREE_OF_RESTORATION.col()))
                      .graderNotes(record.get(ConditionFields.CGC_GRADER_NOTES.col()))
                      .build());
          } else if (certificationCompany == GradingCompany.CBCS) {
              condition
                  .isGraded(true)
                  .cbcsCondition(CBCSCondition.builder()
                      .label(record.get(ConditionFields.CBCS_LABEL.col()))
                      .grade(parseComicGrade(record.get(ConditionFields.CBCS_GRADE.col())))
                      .pageQuality(parsePageQuality(record.get(ConditionFields.CBCS_PAGE_QUALITY.col())))
                      .pedigree(record.get(ConditionFields.CBCS_PEDIGREE.col()))
                      .signature(parseBoolean(record.get(ConditionFields.CBCS_SIGNATURE.col())))
                      .degreeOfRestoration(record.get(ConditionFields.CBCS_DEGREE_OF_RESTORATION.col()))
                      .build());
          } else {
              condition.isGraded(false);
          }
          comicBook.setComicCondition(condition.build());
          comicBook.getComicCondition().syncCondition();

          // Dates and purchase info
          comicBook.setDateAcquired(record.get(GoCollectFields.DATE_ACQUIRED.col()));
          comicBook.setDateSold(record.get(GoCollectFields.DATE_SOLD.col()));
          comicBook.setPurchasedFrom(record.get(GoCollectFields.PURCHASED_FROM.col()));
          comicBook.setPurchaseReferenceURL(record.get(GoCollectFields.PURCHASE_REFERENCE_URL.col()));
          comicBook.setPublishedDate("");

          // Notes
          comicBook.setPersonalNotes(record.get(GoCollectFields.PERSONAL_NOTES.col()));
          comicBook.setPublicNotes(record.get(GoCollectFields.PUBLIC_NOTES.col()));
          comicBook.setImageNotes(record.get(GoCollectFields.IMAGE_NOTES.col()));

          // Images (defaults)
          comicBook.setSmallCachedImageId("");
          comicBook.setLargeCachedImageId("");

          // Cert ID uniqueness check — only for graded comics
          if (Boolean.TRUE.equals(comicBook.getComicCondition().getIsGraded())
              && isNotBlank(comicBook.getComicCondition().getCertificationId())
              && comicService.findByCertificationId(comicBook.getComicCondition().getCertificationId()).isPresent()) {
            duplicates.add(comicBook);
            log.info("Skipping duplicate cert ID: {}", comicBook.getComicCondition().getCertificationId());
            continue;
          }

          // Duplicate check
          if (existingComics.contains(comicBook)) {
            duplicates.add(comicBook);
            log.info("Skipping duplicate comic: {} {}", comicBook.getTitle(), comicBook.getNumber());
            continue;
          }

          comicService.uploadComic(comicBook);
          existingComics.add(comicBook);
          loaded.add(comicBook);
          log.info("Loaded comic: {} {}", comicBook.getTitle(), comicBook.getNumber());
        } catch (Exception e) {
          log.error("Failed to load record: {}", record, e);
          ComicBook failed = new ComicBook();
          failed.setTitle(record.isMapped(ComicFields.COMIC.col()) ? record.get(ComicFields.COMIC.col()) : "Unknown");
          failedToLoad.add(failed);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Unknown error occurred during comic load process.", e);
    }

    Map<String, Object> results = new HashMap<>();
    results.put("succeeded", loaded);
    results.put("failed", failedToLoad);
    results.put("duplicates", duplicates);
    return request.createResponseBuilder(HttpStatus.OK)
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "*")
        .header("Access-Control-Allow-Headers", "Content-Type")
        .body(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results))
        .build();
  }

}
