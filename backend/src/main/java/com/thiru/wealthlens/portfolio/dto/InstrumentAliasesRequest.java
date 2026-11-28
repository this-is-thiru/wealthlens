package com.thiru.wealthlens.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** The aliases one call registers (W-440b): all of them or, on any refusal, none. */
public record InstrumentAliasesRequest(@NotEmpty List<@Valid InstrumentAliasRequest> aliases) {
}
