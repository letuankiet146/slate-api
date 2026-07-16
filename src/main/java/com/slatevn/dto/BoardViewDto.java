package com.slatevn.dto;

import java.util.List;
import java.util.Set;

public record BoardViewDto(
        BoardDto board,
        List<ColumnDto> columns,
        List<TaskTemplateDto> templates,
        List<TaskDto> tasks,
        Set<String> permissions
) {
}
