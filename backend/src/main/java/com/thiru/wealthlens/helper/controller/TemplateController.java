package com.thiru.wealthlens.helper.controller;

import com.thiru.wealthlens.shared.dto.ShippingLabel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

@RestController
@RequestMapping("/template")
public class TemplateController {

    private final TemplateEngine templateEngine;

    public TemplateController(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @GetMapping("/generate-label")
    public String generateShippingLabel(Model model) {
        ShippingLabel shippingLabel = getShippingData();
        model.addAttribute("shippingLabel", shippingLabel);
        return "shipping-label"; // This matches your template name
    }

    @GetMapping("/download-label")
    public ResponseEntity<byte[]> downloadShippingLabel() throws IOException {
        // Prepare data
        Context context = new Context();
        ShippingLabel shippingLabel = getShippingData();
        context.setVariable("shippingLabel", shippingLabel);

        // Process the template
        String processedHtml = templateEngine.process("shipping-label", context);

        // Generate PDF
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(processedHtml);
        renderer.layout();
        renderer.createPDF(outputStream);
        outputStream.close();

        // Prepare response
        byte[] pdfBytes = outputStream.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename("shipping-label.pdf").build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    public ShippingLabel getShippingData() {
        // Create sample data - in a real app, this would come from a service
        return ShippingLabel.builder()
                // Sender Information
                .senderName("John Smith")
                .senderCompany("ABC Corp")
                .senderAddress1("123 Main St")
                .senderCity("Boston")
                .senderState("MA")
                .senderZip("02108")
                .senderCountry("USA")

                // Recipient Information
                .recipientName("Jane Doe")
                .recipientCompany("XYZ Inc")
                .recipientAddress1("456 Oak Ave")
                .recipientCity("San Francisco")
                .recipientState("CA")
                .recipientZip("94102")
                .recipientCountry("USA")

                // Shipping Information
                .trackingNumber("1Z999AA10123456784")
                .serviceType("Priority Overnight")
                .weight("2.5")
                .packageId("PKG20230001")
                .dimensions("10x8x6 in")
                .contents("Electronics")
                .notes("Fragile - This side up")
                .build();
    }
}
