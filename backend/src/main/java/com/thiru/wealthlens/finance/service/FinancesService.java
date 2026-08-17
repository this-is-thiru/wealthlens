package com.thiru.wealthlens.finance.service;


import com.thiru.wealthlens.finance.dto.FinanceRequest;
import com.thiru.wealthlens.finance.dto.FinanceResponse;
import com.thiru.wealthlens.finance.dto.enums.CalculationType;
import org.springframework.stereotype.Service;

@Service
public class FinancesService {

    public FinanceResponse getFinanceResponse(FinanceRequest financeRequest) {

        CalculationType calculationType = financeRequest.getCalculationType();
        return switch (calculationType) {
            case EMI -> calculateEmi(financeRequest);
            case TOTAL_AMOUNT -> calculateFinalAmount(financeRequest);
            case PRINCIPAL_AMOUNT -> calculatePrincipal(financeRequest);
            case RATE_OF_INTEREST ->
                    FinanceResponse.builder().rateOfInterest(financeRequest.getRateOfInterest()).build();
            case TIME_PERIOD -> calculateTimePeriod(financeRequest);
            case NONE -> throw new IllegalArgumentException("Invalid calculation type");
        };
    }

    private FinanceResponse calculateEmi(FinanceRequest financeRequest) {

        validateFinanceInputs(financeRequest, CalculationType.EMI);
        double emiAmount = calculatedEmi(financeRequest);
        return FinanceResponse.builder().emiAmount((int) Math.ceil(emiAmount)).build();
    }

    private FinanceResponse calculateFinalAmount(FinanceRequest financeRequest) {
        validateFinanceInputs(financeRequest, CalculationType.TOTAL_AMOUNT);
        double finalAmount = calculatedEmi(financeRequest) * financeRequest.getTimePeriod();
        return FinanceResponse.builder().finalAmount(finalAmount).build();
    }

    private FinanceResponse calculatePrincipal(FinanceRequest financeRequest) {
        validateFinanceInputs(financeRequest, CalculationType.PRINCIPAL_AMOUNT);

        double emiAmount = financeRequest.getEmiAmount();
        double percentageOfRateOfInterest = financeRequest.getRateOfInterest();
        double rateOfInterestPerMonth = percentageOfRateOfInterest / (12 * 100);
        int timePeriod = financeRequest.getTimePeriod();

        double principalAmount;
        if (rateOfInterestPerMonth == 0) {
            principalAmount = emiAmount * timePeriod;
        } else {
            principalAmount = emiAmount * (Math.pow(1 + rateOfInterestPerMonth, timePeriod) - 1)
                    / (rateOfInterestPerMonth * Math.pow(1 + rateOfInterestPerMonth, timePeriod));
        }

        double rounded = Math.round(principalAmount * 100.0) / 100.0;
        return FinanceResponse.builder().principalAmount(rounded).build();
    }

    private FinanceResponse calculateTimePeriod(FinanceRequest financeRequest) {
        validateFinanceInputs(financeRequest, CalculationType.TIME_PERIOD);

        double principalAmount = financeRequest.getPrincipalAmount();
        double emiAmount = financeRequest.getEmiAmount();
        double percentageOfRateOfInterest = financeRequest.getRateOfInterest();
        double rateOfInterestPerMonth = percentageOfRateOfInterest / (12 * 100);

        int timePeriod;
        if (rateOfInterestPerMonth == 0) {
            timePeriod = (int) Math.ceil(principalAmount / emiAmount);
        } else {
            double n = Math.log(emiAmount / (emiAmount - principalAmount * rateOfInterestPerMonth))
                    / Math.log(1 + rateOfInterestPerMonth);
            timePeriod = (int) Math.ceil(n);
        }

        return FinanceResponse.builder().timePeriod(timePeriod).build();
    }

    private double calculatedEmi(FinanceRequest financeRequest) {
        double principalAmount = financeRequest.getPrincipalAmount();
        double percentageOfRateOfInterest = financeRequest.getRateOfInterest();
        double rateOfInterestPerMonth = percentageOfRateOfInterest / (12 * 100);
        int timePeriod = financeRequest.getTimePeriod();

        if (rateOfInterestPerMonth == 0) {
            return principalAmount / timePeriod;
        }

        return principalAmount * rateOfInterestPerMonth
                * Math.pow(1 + rateOfInterestPerMonth, timePeriod) / (Math.pow(1 + rateOfInterestPerMonth, timePeriod) - 1);
    }

    private void validateFinanceInputs(FinanceRequest financeRequest, CalculationType calculationType) {
        switch (calculationType) {
            case EMI, TOTAL_AMOUNT -> {
                if (financeRequest.getPrincipalAmount() < 0) {
                    throw new IllegalArgumentException("Principal amount must be non-negative");
                }
                if (financeRequest.getRateOfInterest() < 0) {
                    throw new IllegalArgumentException("Rate of interest must be non-negative");
                }
                if (financeRequest.getTimePeriod() <= 0) {
                    throw new IllegalArgumentException("Time period must be greater than zero");
                }
            }
            case PRINCIPAL_AMOUNT -> {
                if (financeRequest.getEmiAmount() <= 0) {
                    throw new IllegalArgumentException("EMI amount must be greater than zero");
                }
                if (financeRequest.getRateOfInterest() < 0) {
                    throw new IllegalArgumentException("Rate of interest must be non-negative");
                }
                if (financeRequest.getTimePeriod() <= 0) {
                    throw new IllegalArgumentException("Time period must be greater than zero");
                }
            }
            case TIME_PERIOD -> {
                if (financeRequest.getPrincipalAmount() <= 0) {
                    throw new IllegalArgumentException("Principal amount must be greater than zero");
                }
                if (financeRequest.getEmiAmount() <= 0) {
                    throw new IllegalArgumentException("EMI amount must be greater than zero");
                }
                if (financeRequest.getRateOfInterest() < 0) {
                    throw new IllegalArgumentException("Rate of interest must be non-negative");
                }
                double rateOfInterestPerMonth = financeRequest.getRateOfInterest() / (12 * 100);
                if (financeRequest.getEmiAmount() <= financeRequest.getPrincipalAmount() * rateOfInterestPerMonth) {
                    throw new IllegalArgumentException("EMI must be greater than principal times monthly rate");
                }
            }
            default -> {
            }
        }
    }
}
