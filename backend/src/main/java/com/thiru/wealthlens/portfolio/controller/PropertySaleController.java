package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.PropertySaleRequest;
import com.thiru.wealthlens.portfolio.service.PropertySaleService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where a land or building's sale is entered, since it is not a broker trade (W-088g9, D-434). */
@RequiredArgsConstructor
@RequestMapping("/properties/user/{email}")
@RestController
public class PropertySaleController {

    private final PropertySaleService propertySaleService;

    @PostMapping("/sales")
    public String recordSale(@PathVariable String email, @RequestBody PropertySaleRequest request) {
        return propertySaleService.recordSale(UserMail.from(email), request);
    }
}
