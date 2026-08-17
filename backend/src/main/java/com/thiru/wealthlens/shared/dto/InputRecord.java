package com.thiru.wealthlens.shared.dto;

import com.thiru.wealthlens.shared.util.parser.CellDetail;
import java.util.Map;
import lombok.Data;

@Data
public class InputRecord {

	private Map<String, CellDetail> record;
	private Integer recordNumber;
}
