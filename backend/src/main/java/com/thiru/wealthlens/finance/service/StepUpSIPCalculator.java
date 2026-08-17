package com.thiru.wealthlens.finance.service;


import com.thiru.wealthlens.finance.dto.InterestRateResponse;
import com.thiru.wealthlens.finance.dto.StepUpSIPRequest;
import java.text.DecimalFormat;
import org.springframework.stereotype.Service;

@Service
public class StepUpSIPCalculator {

    public InterestRateResponse calculateRate(StepUpSIPRequest request) {
        double tolerance = 0.00001;
        int maxIterations = 100;

        double totalPrincipal = calculateTotalPrincipal(request);
        double initialGuess = (request.getTargetAmount() > totalPrincipal) ? 0.01 : -0.05;

        double rate = newtonRaphson(request, initialGuess, tolerance, maxIterations);
        double yearlyRate = Math.pow(1 + rate, 12) - 1;

        DecimalFormat df = new DecimalFormat("#.00");
        String monthlyRate = df.format(rate * 100);
        String annualRate = df.format(yearlyRate * 100);

        return new InterestRateResponse(Double.parseDouble(monthlyRate), Double.parseDouble(annualRate));
    }

    private double calculateMonthlyRate(StepUpSIPRequest request) {
        double tolerance = 0.00001;
        double rate = 0.01; // Initial guess (1%)

        for (int iter = 0; iter < 100; iter++) {
            double fv = 0;
            double stepUp = 1;

            for (int m = 1; m <= request.getMonths(); m++) {
                double sip = request.getInitialAmount() * stepUp;
                fv += sip * Math.pow(1 + rate, request.getMonths() - m + 1);
                stepUp *= (1 + request.getStepUpRate());
            }

            if (Math.abs(fv - request.getTargetAmount()) < tolerance) {
                return rate;
            }
            rate += (request.getTargetAmount() - fv) / request.getTargetAmount() * 0.001;
        }
        return rate;
    }

    private double calculateTotalPrincipal(StepUpSIPRequest request) {
        int frequencyMonths = request.getStepUpFrequency().getValue();
        double rate = request.getStepUpRate();
        double initial = request.getInitialAmount();
        int totalMonths = request.getMonths();

        if (rate == 0) {
            return initial * totalMonths;
        }

        int totalStepUps = totalMonths / frequencyMonths;
        int remainingMonths = totalMonths % frequencyMonths;

        double sum = initial * frequencyMonths * (Math.pow(1 + rate, totalStepUps) - 1) / rate;

        if (remainingMonths > 0) {
            sum += initial * Math.pow(1 + rate, totalStepUps) * remainingMonths;
        }

        return sum;
    }

    private double newtonRaphson(StepUpSIPRequest request, double initialGuess, double tolerance, int maxIterations) {
        double rate = initialGuess;
        for (int i = 0; i < maxIterations; i++) {
            double fv = calculateFV(request, rate);
            double fvDerivative = calculateFVDerivative(request, rate);
            double newRate = rate - (fv - request.getTargetAmount()) / fvDerivative;

            if (Math.abs(newRate - rate) < tolerance) {
                return newRate;
            }
            rate = newRate;
        }
        return rate;
    }

    private double calculateFVDerivative(StepUpSIPRequest request, double monthlyRate) {
        double h = 0.0001;
        double fv1 = calculateFV(request, monthlyRate + h);
        double fv2 = calculateFV(request, monthlyRate);
        return (fv1 - fv2) / h;
    }

    private double calculateFV(StepUpSIPRequest request, double monthlyRate) {
        double fv = 0.0;
        int frequencyMonths = request.getStepUpFrequency().getValue();
        double currentSIP = request.getInitialAmount();

        for (int month = 1; month <= request.getMonths(); month++) {
            // Apply step-up at the start of each frequency period (e.g., Yearly = every 12 months)
            if (month > 1 && (month - 1) % frequencyMonths == 0) {
                currentSIP *= (1 + request.getStepUpRate());
            }

            int remainingMonths = request.getMonths() - month + 1;
            fv += currentSIP * Math.pow(1 + monthlyRate, remainingMonths);
        }

        return fv;
    }
}
