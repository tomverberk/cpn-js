package com.indevstudio.cpnide.server.model;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class CreateLogResp {
    @Getter
    String extraInfo;
    @Getter
    List<HtmlFileContent> files;
}
