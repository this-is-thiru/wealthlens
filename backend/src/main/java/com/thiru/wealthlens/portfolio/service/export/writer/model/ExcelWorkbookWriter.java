package com.thiru.wealthlens.portfolio.service.export.writer.model;

import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.util.List;
import org.springframework.core.io.InputStreamResource;

public interface ExcelWorkbookWriter<EntityType extends AuditableEntity> {

    InputStreamResource process(List<EntityType> entities);

}
