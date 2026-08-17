package com.thiru.wealthlens.portfolio.service.export.processor.model;

import com.thiru.wealthlens.helper.file.FileStream;
import com.thiru.wealthlens.helper.file.FileType;
import com.thiru.wealthlens.portfolio.service.export.writer.model.ExcelWorkbookWriter;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;

public abstract class AbstractExcelWorkbookProcessor<EntityType extends AuditableEntity> implements ExcelWorkbookProcessor {

    private static final String ENV_UAT = "uat";
    private static final String ENV_PROD = "prod";
    private static final String FILE_NAME_DATE_FORMAT = "dd-MMMM-yyyy--hh-mm-ss-a";
    private final String fileName;
    private final FileType fileType;
    private final Environment env;

    @Getter(value = AccessLevel.PROTECTED)
    private final UserMail userMail;

    public AbstractExcelWorkbookProcessor(UserMail userMail, String fileName, FileType fileType, Environment env) {
        this.userMail = userMail;
        this.fileName = fileName;
        this.fileType = fileType;
        this.env = env;
    }

    protected abstract ExcelWorkbookWriter<EntityType> workbookWriter();

    protected abstract List<EntityType> entities();

    @Override
    public FileStream fileStream() {
        InputStreamResource inputStreamResource = workbookWriter().process(this.entities());
        return FileStream.from(fileName(), inputStreamResource, fileType);
    }

    protected String fileName() {
        String fileNameDateComponent = TLocalDateTime.format(LocalDateTime.now(), FILE_NAME_DATE_FORMAT);
        Set<String> activeProfiles = Set.of(env.getActiveProfiles());
        boolean isProdEnv = activeProfiles.contains(ENV_PROD);
        String fileNamePrefix = isProdEnv ? this.fileName : ENV_UAT + "-" + this.fileName;
        return fileNamePrefix + fileNameDateComponent + fileType.getExtension();
    }
}
