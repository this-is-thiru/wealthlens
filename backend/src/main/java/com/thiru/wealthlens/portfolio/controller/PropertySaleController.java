package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.PropertySaleRequest;
import com.thiru.wealthlens.portfolio.service.PropertySaleService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Where a land or building's sale is entered, since it is not a broker trade (W-088g9, D-434). */
@RequiredArgsConstructor
@MeApi
@RestController
public class PropertySaleController {

    private final PropertySaleService propertySaleService;

    @PostMapping("/properties/sales")
    public String recordSale(@CallerEmail String email, @RequestBody PropertySaleRequest request) {
        return propertySaleService.recordSale(UserMail.from(email), request);
    }
}
