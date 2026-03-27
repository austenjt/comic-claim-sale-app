package org.example.functions.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.example.functions.model.ComicBook;
import org.example.functions.model.ComicNumber;
import org.example.functions.model.enums.ComicGrade;
import org.example.functions.model.enums.GradingCompany;
import org.example.functions.service.ComicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsvToJsonConverterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ComicService comicService;

    @Mock
    private HttpRequestMessage<Optional<String>> request;

    @Mock
    private HttpResponseMessage.Builder responseBuilder;

    @Mock
    private HttpResponseMessage response;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    private String loadCsv(String filename) throws Exception {
        InputStream is = Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(filename),
            "Test resource not found: " + filename);
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private ArgumentCaptor<Object> stubResponseChain() {
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        when(request.createResponseBuilder(HttpStatus.OK)).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(bodyCaptor.capture())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(response);
        return bodyCaptor;
    }

    @Test
    void loadGoCollectCsvData_cgcSlabs_allSucceed() throws Exception {
        String csvData = loadCsv("test-cgc-slabs.csv");
        when(comicService.getComicsList()).thenReturn(Collections.emptyList());
        when(comicService.uploadComic(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Object> bodyCaptor = stubResponseChain();

        CsvToJsonConverter converter = new CsvToJsonConverter(csvData, comicService);
        converter.loadGoCollectCsvData(request, null, false);

        // Verify all 12 records were uploaded
        ArgumentCaptor<ComicBook> comicCaptor = ArgumentCaptor.forClass(ComicBook.class);
        verify(comicService, times(12)).uploadComic(comicCaptor.capture());

        // Assert response body buckets
        String json = (String) bodyCaptor.getValue();
        JsonNode root = OBJECT_MAPPER.readTree(json);
        assertEquals(12, root.get("succeeded").size());
        assertEquals(0, root.get("failed").size());
        assertEquals(0, root.get("duplicates").size());

        List<ComicBook> captured = comicCaptor.getAllValues();

        // Spot-check a CGC record: Akira (was "Akira #1" in CSV — number stripped from title)
        ComicBook akira1 = captured.stream()
            .filter(c -> "Akira".equals(c.getTitle()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Akira not found in captured comics"));
        assertTrue(akira1.getComicCondition().getIsGraded());
        assertEquals(GradingCompany.CGC, akira1.getComicCondition().getCertificationCompany());
        assertEquals("4308605002", akira1.getComicCondition().getCertificationId());
        assertNotNull(akira1.getComicCondition().getCgcCondition());
        assertNull(akira1.getComicCondition().getCbcsCondition());

        // Spot-check a CBCS record: Elementals (was "Elementals #1" in CSV — number stripped from title)
        ComicBook elementals1 = captured.stream()
            .filter(c -> "Elementals".equals(c.getTitle()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Elementals not found in captured comics"));
        assertTrue(elementals1.getComicCondition().getIsGraded());
        assertEquals(GradingCompany.CBCS, elementals1.getComicCondition().getCertificationCompany());
        assertEquals("22-2437CAB-001", elementals1.getComicCondition().getCertificationId());
        assertNotNull(elementals1.getComicCondition().getCbcsCondition());
        assertNull(elementals1.getComicCondition().getCgcCondition());
    }

    @Test
    void reconcileTitleWithNumber_stripsNumberFromMiddle_amazingSpiderMan() {
        CsvToJsonConverter converter = new CsvToJsonConverter("", comicService);
        ComicNumber number = ComicNumber.of(-1, 222);
        String result = converter.reconcileTitleWithNumber(
            "The Amazing Spider-Man #222 (Newsstand Edition)", number);
        assertEquals("The Amazing Spider-Man (Newsstand Edition)", result);
    }

    @Test
    void reconcileTitleWithNumber_stripsNumberFromMiddle_brzrkr() {
        CsvToJsonConverter converter = new CsvToJsonConverter("", comicService);
        ComicNumber number = ComicNumber.of(-1, 1);
        String result = converter.reconcileTitleWithNumber(
            "BRZRKR #1 (Berzerker) (Thank You Variant)", number);
        assertEquals("BRZRKR (Berzerker) (Thank You Variant)", result);
    }

    @Test
    void loadGoCollectCsvData_rawBooks_allSucceed() throws Exception {
        String csvData = loadCsv("test-raw-books.csv");
        when(comicService.getComicsList()).thenReturn(Collections.emptyList());
        when(comicService.uploadComic(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Object> bodyCaptor = stubResponseChain();

        CsvToJsonConverter converter = new CsvToJsonConverter(csvData, comicService);
        converter.loadGoCollectCsvData(request, null, false);

        // Verify all 19 records were uploaded
        ArgumentCaptor<ComicBook> comicCaptor = ArgumentCaptor.forClass(ComicBook.class);
        verify(comicService, times(19)).uploadComic(comicCaptor.capture());

        // Assert response body buckets
        String json = (String) bodyCaptor.getValue();
        JsonNode root = OBJECT_MAPPER.readTree(json);
        assertEquals(19, root.get("succeeded").size());
        assertEquals(0, root.get("failed").size());
        assertEquals(0, root.get("duplicates").size());

        List<ComicBook> captured = comicCaptor.getAllValues();

        // All records must be not graded (Not Certified)
        assertTrue(captured.stream().allMatch(c -> !c.getComicCondition().getIsGraded()),
            "All raw books should have isGraded=false");

        // Spot-check: Adventures of Cyclops and Phoenix #1 should have notCertifiedGrade = 9.8
        ComicBook cyclops = captured.stream()
            .filter(c -> c.getTitle().startsWith("Adventures of Cyclops"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Adventures of Cyclops not found in captured comics"));
        assertFalse(cyclops.getComicCondition().getIsGraded());
        assertEquals(ComicGrade.NEAR_MINT_MINT, cyclops.getComicCondition().getNotCertifiedGrade());
        assertNull(cyclops.getComicCondition().getCgcCondition());
        assertNull(cyclops.getComicCondition().getCbcsCondition());
    }
}
