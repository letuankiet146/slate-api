package com.slatevn.web;

import com.slatevn.dto.BoardDto;
import com.slatevn.dto.BoardMemberDto;
import com.slatevn.dto.BoardViewDto;
import com.slatevn.dto.ColumnDto;
import com.slatevn.dto.CreateBoardRequest;
import com.slatevn.dto.CreateColumnRequest;
import com.slatevn.dto.CreateTaskRequest;
import com.slatevn.dto.ReorderColumnsRequest;
import com.slatevn.dto.TaskDto;
import com.slatevn.dto.UpdateBoardRequest;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.BoardService;
import com.slatevn.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BoardController {

    private final BoardService boardService;
    private final TaskService taskService;

    public BoardController(BoardService boardService, TaskService taskService) {
        this.boardService = boardService;
        this.taskService = taskService;
    }

    @GetMapping("/workspaces/{workspaceId}/boards")
    public List<BoardDto> listBoards(@PathVariable UUID workspaceId) {
        return boardService.listByWorkspace(SecurityUtils.currentUser().getId(), workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/boards")
    public BoardDto createBoard(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateBoardRequest request
    ) {
        return boardService.create(SecurityUtils.currentUser().getId(), workspaceId, request);
    }

    @GetMapping("/workspaces/{workspaceId}/deleted-tasks")
    public List<TaskDto> listDeletedTasksInWorkspace(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) List<UUID> boardIds
    ) {
        return taskService.listDeletedInWorkspace(
                SecurityUtils.currentUser().getId(),
                workspaceId,
                boardIds
        );
    }

    @GetMapping("/boards/{boardId}")
    public BoardViewDto getBoard(@PathVariable UUID boardId) {
        return boardService.getView(SecurityUtils.currentUser().getId(), boardId);
    }

    @PutMapping("/boards/{boardId}")
    public BoardDto updateBoard(
            @PathVariable UUID boardId,
            @Valid @RequestBody UpdateBoardRequest request
    ) {
        return boardService.update(SecurityUtils.currentUser().getId(), boardId, request);
    }

    @DeleteMapping("/boards/{boardId}")
    public void deleteBoard(@PathVariable UUID boardId) {
        boardService.softDelete(SecurityUtils.currentUser().getId(), boardId);
    }

    @PostMapping("/boards/{boardId}/restore")
    public BoardDto restoreBoard(@PathVariable UUID boardId) {
        return boardService.restore(SecurityUtils.currentUser().getId(), boardId);
    }

    @GetMapping("/boards/{boardId}/members")
    public List<BoardMemberDto> listMembers(@PathVariable UUID boardId) {
        return boardService.listMembers(SecurityUtils.currentUser().getId(), boardId);
    }

    @GetMapping("/boards/{boardId}/columns")
    public List<ColumnDto> listColumns(@PathVariable UUID boardId) {
        return boardService.listColumns(SecurityUtils.currentUser().getId(), boardId);
    }

    @PostMapping("/boards/{boardId}/columns")
    public ColumnDto createColumn(
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateColumnRequest request
    ) {
        return boardService.createColumn(SecurityUtils.currentUser().getId(), boardId, request);
    }

    @PutMapping("/boards/{boardId}/columns/reorder")
    public List<ColumnDto> reorderColumns(
            @PathVariable UUID boardId,
            @Valid @RequestBody ReorderColumnsRequest request
    ) {
        return boardService.reorderColumns(
                SecurityUtils.currentUser().getId(),
                boardId,
                request.columnIds()
        );
    }

    @DeleteMapping("/boards/{boardId}/columns/{columnId}")
    public void deleteColumn(@PathVariable UUID boardId, @PathVariable UUID columnId) {
        boardService.deleteColumn(SecurityUtils.currentUser().getId(), boardId, columnId);
    }

    @GetMapping("/boards/{boardId}/deleted-tasks")
    public List<TaskDto> listDeletedTasks(@PathVariable UUID boardId) {
        return taskService.listDeleted(SecurityUtils.currentUser().getId(), boardId);
    }

    @PostMapping("/boards/{boardId}/tasks")
    public TaskDto createTask(
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return taskService.create(SecurityUtils.currentUser().getId(), boardId, request);
    }
}
