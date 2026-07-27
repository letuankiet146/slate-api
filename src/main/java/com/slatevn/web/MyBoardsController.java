package com.slatevn.web;

import com.slatevn.dto.ManagedBoardDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.BoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/my-boards")
public class MyBoardsController {

    private final BoardService boardService;

    public MyBoardsController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public List<ManagedBoardDto> list() {
        return boardService.listManaged(SecurityUtils.currentUser().getId());
    }
}
