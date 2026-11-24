package com.thiru.wealthlens.portfolio.entity;

import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/**
 * Serializes {@code List<OrderTimeQuantity>} to and from a {@code TEXT} JSON column (W-337e, D-949 C2a's pattern):
 * no query anywhere filters inside a lot's order-time breakdown.
 */
@Converter
public class OrderTimeQuantityListConverter extends JsonColumnConverter<List<OrderTimeQuantity>> {

    private static final TypeReference<List<OrderTimeQuantity>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<List<OrderTimeQuantity>> type() {
        return TYPE;
    }

    @Override
    protected List<OrderTimeQuantity> empty() {
        return new ArrayList<>();
    }
}
