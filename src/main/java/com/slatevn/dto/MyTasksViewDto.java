package com.slatevn.dto;

import java.util.List;

public record MyTasksViewDto(
        List<MyTasksColumnDto> columns,
        List<MyTasksTaskDto> tasks,
        boolean readOnly
) {
}
