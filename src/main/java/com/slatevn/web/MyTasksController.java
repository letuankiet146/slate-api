package com.slatevn.web;

import com.slatevn.dto.MyTasksMoveRequest;
import com.slatevn.dto.MyTasksTaskDto;
import com.slatevn.dto.MyTasksViewDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.MyTasksService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/my-tasks")
public class MyTasksController {

    private final MyTasksService myTasksService;

    public MyTasksController(MyTasksService myTasksService) {
        this.myTasksService = myTasksService;
    }

    @GetMapping
    public MyTasksViewDto getView() {
        return myTasksService.getView(SecurityUtils.currentUser().getId());
    }

    @PostMapping("/{taskId}/move")
    public MyTasksTaskDto move(
            @PathVariable UUID taskId,
            @Valid @RequestBody MyTasksMoveRequest request
    ) {
        return myTasksService.move(SecurityUtils.currentUser().getId(), taskId, request);
    }
}
