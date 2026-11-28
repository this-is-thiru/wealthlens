package com.thiru.wealthlens.helper.file;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@UtilityClass
public class FileHelper {

    public static ResponseEntity<InputStreamResource> sendFileAsAttachment(FileStream fileStream) {

        String headerValue = "attachment; filename=" + fileStream.getFileName();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .contentType(fileStream.getFileType().getMediaType()).body(fileStream.getInputStreamResource());
    }

}
