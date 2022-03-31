package com.indevstudio.cpnide.server.createLog;

import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Data
public class CreateLogConfig {
    String caseId;
    String startDateTime;
    String timeUnit;
}
