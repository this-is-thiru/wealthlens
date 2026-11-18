package com.thiru.wealthlens.taxplanning.workspace.importing;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.isolation.IsolatedChild;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The salary workbook's child process, under the same limits as a PDF's ({@code app.tax.documents.isolation}; W-133a C3a). */
@Configuration
public class SalaryImportConfig {

    @Bean
    public IsolatedChild salaryWorkbookReader(DocumentSettings settings) {
        return IsolatedChild.of(SalaryStructureWorkbook.class, settings.isolation());
    }
}
