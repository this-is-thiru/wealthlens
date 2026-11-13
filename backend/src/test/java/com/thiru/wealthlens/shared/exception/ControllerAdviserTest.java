package com.thiru.wealthlens.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * A request the caller got wrong answers with its own status and says what is accepted (W-263). Each bound type the
 * controllers use has a parameter on the stub below; the expected messages are written from the parameter's name and
 * type, not from a run. A body the server cannot read answers the same way, naming the field (W-305).
 */
class ControllerAdviserTest {

    enum Frequency {
        ANNUALLY,
        QUARTERLY
    }

    @RestController
    static class Stub {

        @GetMapping("/years/{taxYear}")
        String year(@PathVariable int taxYear) {
            return "year";
        }

        @GetMapping("/bound")
        String bound(@RequestParam(defaultValue = "true") boolean dryRun,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate billedThrough,
                @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Optional<LocalDate> asOf,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedBefore,
                @RequestParam(required = false) Frequency frequency, @RequestParam(defaultValue = "0") long offset) {
            return "bound";
        }

        @PostMapping("/upload")
        String upload(@RequestParam("file") MultipartFile file) {
            return "upload";
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody String body) {
            return "body";
        }

        @PostMapping(value = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
        String order(@RequestBody Order order) {
            return "order";
        }

        @PostMapping(value = "/dates", consumes = MediaType.APPLICATION_JSON_VALUE)
        String dates(@RequestBody List<LocalDate> dates) {
            return "dates";
        }
    }

    record Rule(String code, boolean perLot) {
    }

    record Order(double price, Frequency frequency, LocalDate tradeDate, List<Rule> rules) {
    }

    /** Bound as the application binds a body: an unknown field and a null primitive are refused, not defaulted. */
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub()).setControllerAdvice(new ControllerAdviser())
            .setMessageConverters(new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter(STRICT)).build();

    private void expectBodyRefusal(String uri, String body, String message) throws Exception {
        mvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").value(message));
    }

    private void expectRefusal(String uri, String message) throws Exception {
        mvc.perform(get(uri)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void handleTypeMismatch_whenAnIntIsNotANumber_answers400NamingAWholeNumber() throws Exception {
        expectRefusal("/years/2026-27", "Parameter 'taxYear' must be a whole number, but was '2026-27'");
    }

    @Test
    void handleTypeMismatch_whenABooleanIsNeither_answers400NamingTrueOrFalse() throws Exception {
        expectRefusal("/bound?dryRun=maybe", "Parameter 'dryRun' must be true or false, but was 'maybe'");
    }

    @Test
    void handleTypeMismatch_whenADateIsNotOne_answers400NamingItsFormat() throws Exception {
        expectRefusal("/bound?billedThrough=01-11-2026",
                "Parameter 'billedThrough' must be a date as yyyy-MM-dd, but was '01-11-2026'");
    }

    @Test
    void handleTypeMismatch_whenAnOptionalDateIsNotOne_answers400NamingTheDateNotTheOptional() throws Exception {
        expectRefusal("/bound?asOf=June", "Parameter 'asOf' must be a date as yyyy-MM-dd, but was 'June'");
    }

    @Test
    void handleTypeMismatch_whenADateTimeIsNotOne_answers400NamingItsFormat() throws Exception {
        expectRefusal("/bound?recordedBefore=yesterday",
                "Parameter 'recordedBefore' must be a date and time as yyyy-MM-dd'T'HH:mm:ss, but was 'yesterday'");
    }

    @Test
    void handleTypeMismatch_whenAnEnumIsNotAConstant_answers400ListingTheConstants() throws Exception {
        expectRefusal("/bound?frequency=WEEKLY",
                "Parameter 'frequency' must be one of [ANNUALLY, QUARTERLY], but was 'WEEKLY'");
    }

    @Test
    void handleTypeMismatch_whenTheTypeHasNoWording_answers400NamingTheType() throws Exception {
        expectRefusal("/bound?offset=far", "Parameter 'offset' must be a long, but was 'far'");
    }

    @Test
    void handleTypeMismatch_whenEveryValueFits_isNotReached() throws Exception {
        mvc.perform(get("/bound?dryRun=false&billedThrough=2026-11-01&asOf=2026-11-11"
                + "&recordedBefore=2026-11-01T10:15:30&frequency=QUARTERLY&offset=3")).andExpect(status().isOk());
    }

    @Test
    void handleMissingPart_whenTheUploadHasNoFile_answers400NamingThePart() throws Exception {
        mvc.perform(multipart("/upload")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'file' is not present"));
    }

    @Test
    void handleMethodNotAllowed_whenTheMethodIsNotServed_answers405() throws Exception {
        mvc.perform(post("/years/2026")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405)).andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void handleUnsupportedMediaType_whenTheBodyIsOfATypeNotRead_answers415() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_PDF).content("x")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415)).andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    @Test
    void handleUnreadableBody_whenThereIsNoBody_answers400SayingSo() throws Exception {
        mvc.perform(post("/order").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing"));
    }

    @Test
    void handleUnreadableBody_whenTheBodyIsNotJson_answers400NamingWhereItBreaks() throws Exception {
        // `{"price": }` breaks at the closing brace, the 11th character of line 1
        expectBodyRefusal("/order", "{\"price\": }", "The request body is not valid JSON (line 1, column 11)");
    }

    @Test
    void handleUnreadableBody_whenANumberIsNotOne_answers400NamingTheFieldAndValue() throws Exception {
        expectBodyRefusal("/order", "{\"price\":\"abc\"}", "Field 'price' must be a number, but was 'abc'");
    }

    @Test
    void handleUnreadableBody_whenAFieldIsUnknown_answers400NamingIt() throws Exception {
        // price is given, so the record is not refused for a missing primitive before the unknown field is reached
        expectBodyRefusal("/order", "{\"price\":1,\"nope\":1}", "Field 'nope' is not one this request accepts");
    }

    @Test
    void handleUnreadableBody_whenAnEnumIsNotAConstant_answers400ListingTheConstants() throws Exception {
        expectBodyRefusal("/order", "{\"frequency\":\"WEEKLY\"}",
                "Field 'frequency' must be one of [ANNUALLY, QUARTERLY], but was 'WEEKLY'");
    }

    @Test
    void handleUnreadableBody_whenADateIsNotOne_answers400NamingItsFormat() throws Exception {
        expectBodyRefusal("/order", "{\"tradeDate\":\"2026-13-01\"}",
                "Field 'tradeDate' must be a date as yyyy-MM-dd, but was '2026-13-01'");
    }

    @Test
    void handleUnreadableBody_whenAPrimitiveIsNull_answers400NamingWhatItAccepts() throws Exception {
        expectBodyRefusal("/order", "{\"price\":null}", "Field 'price' must be a number");
    }

    @Test
    void handleUnreadableBody_whenANestedPrimitiveIsMissing_answers400NamingItsPath() throws Exception {
        expectBodyRefusal("/order", "{\"rules\":[{\"code\":\"STT\"}]}", "Field 'rules[0].perLot' must be true or false");
    }

    @Test
    void handleUnreadableBody_whenAnArrayIsSentForAnObject_answers400NamingTheShape() throws Exception {
        expectBodyRefusal("/order", "[]", "The request body must be a JSON object");
    }

    @Test
    void handleUnreadableBody_whenAnObjectIsSentForAList_answers400NamingTheShape() throws Exception {
        expectBodyRefusal("/dates", "{}", "The request body must be a JSON array");
    }
}
