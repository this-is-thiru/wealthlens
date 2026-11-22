package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.analytics.AssetAllocationResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.PerformanceMetricsResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.PortfolioSummaryResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrRequest;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrResponse;
import com.thiru.wealthlens.portfolio.service.AnalyticsService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@MeApi
@RequestMapping("/analytics")
@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/portfolio-summary")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary(@CallerEmail String email) {
        return ResponseEntity.ok(analyticsService.getPortfolioSummary(UserMail.from(email)));
    }

    @GetMapping("/asset-allocation")
    public ResponseEntity<List<AssetAllocationResponse>> getAssetAllocation(@CallerEmail String email) {
        return ResponseEntity.ok(analyticsService.getAssetAllocation(UserMail.from(email)));
    }

    @GetMapping("/performance-metrics")
    public ResponseEntity<PerformanceMetricsResponse> getPerformanceMetrics(@CallerEmail String email) {
        return ResponseEntity.ok(analyticsService.getPerformanceMetrics(UserMail.from(email)));
    }

    @PostMapping("/xirr")
    public ResponseEntity<XirrResponse> calculateXirr(@CallerEmail String email, @Valid @RequestBody XirrRequest request) {
        return ResponseEntity.ok(analyticsService.calculateXirr(UserMail.from(email), request));
    }
}
